package dex

import (
	"fmt"
	"math"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"unicode"
)

type WriteResult struct {
	ClassCount       int
	SkippedCount     int
	FieldCount       int
	MethodCount      int
	CodeMethods      int
	NoCodeMethods    int
	RecoveredMethods int
	ComplexMethods   int
	InstructionCount int
}

type JavaWriteOptions struct {
	BinaryInnerNames bool
	Progress         func(JavaProgress)
	Stats            *WriteResult
}

type JavaProgress struct {
	DexName         string
	ClassDescriptor string
	ClassIndex      int
	ClassTotal      int
	WrittenFiles    int
	JavaPath        string
}

func WriteJavaSkeletons(outDir string, files []*File, opts JavaWriteOptions) (WriteResult, error) {
	var result WriteResult
	sourcesRoot := filepath.Join(outDir, "sources")
	if err := os.MkdirAll(sourcesRoot, 0o755); err != nil {
		return result, err
	}

	var index strings.Builder
	index.WriteString("jadx-go Java output index\n")
	index.WriteString("==========================\n\n")
	seen := map[string]int{}
	for _, f := range files {
		classes := f.Classes()
		sort.Slice(classes, func(i, j int) bool { return classes[i].Descriptor < classes[j].Descriptor })
		index.WriteString(f.Name)
		index.WriteByte('\n')
		for classIndex, cls := range classes {
			result.FieldCount += len(cls.StaticFields) + len(cls.Fields)
			result.MethodCount += len(cls.Direct) + len(cls.Virtual)
			javaPath := javaFilePath(sourcesRoot, cls.Descriptor)
			rel, _ := filepath.Rel(outDir, javaPath)
			if count := seen[javaPath]; count > 0 {
				ext := filepath.Ext(javaPath)
				javaPath = strings.TrimSuffix(javaPath, ext) + fmt.Sprintf("_%d", count+1) + ext
				rel, _ = filepath.Rel(outDir, javaPath)
				result.SkippedCount++
			}
			seen[javaPath]++
			if err := os.MkdirAll(filepath.Dir(javaPath), 0o755); err != nil {
				return result, err
			}
			classOpts := opts
			classOpts.Stats = &result
			text := renderClass(cls, classOpts)
			if err := os.WriteFile(javaPath, []byte(text), 0o644); err != nil {
				return result, err
			}
			result.ClassCount++
			if opts.Progress != nil {
				relProgress, _ := filepath.Rel(outDir, javaPath)
				opts.Progress(JavaProgress{
					DexName:         f.Name,
					ClassDescriptor: cls.Descriptor,
					ClassIndex:      classIndex + 1,
					ClassTotal:      len(classes),
					WrittenFiles:    result.ClassCount,
					JavaPath:        filepath.ToSlash(relProgress),
				})
			}
			index.WriteString("  ")
			index.WriteString(cls.Descriptor)
			index.WriteString(" -> ")
			index.WriteString(filepath.ToSlash(rel))
			index.WriteByte('\n')
		}
		index.WriteByte('\n')
	}
	if err := os.WriteFile(filepath.Join(outDir, "index.txt"), []byte(index.String()), 0o644); err != nil {
		return result, err
	}
	if err := os.WriteFile(filepath.Join(outDir, "jadx-go-summary.txt"), []byte(summaryText(result, files)), 0o644); err != nil {
		return result, err
	}
	return result, nil
}

func (r WriteResult) DebugLine() string {
	return fmt.Sprintf("classes=%d fields=%d methods=%d code_methods=%d recovered=%d complex=%d no_code=%d instructions=%d skipped=%d",
		r.ClassCount, r.FieldCount, r.MethodCount, r.CodeMethods, r.RecoveredMethods, r.ComplexMethods, r.NoCodeMethods, r.InstructionCount, r.SkippedCount)
}

func summaryText(result WriteResult, files []*File) string {
	var b strings.Builder
	b.WriteString("jadx-go summary\n")
	b.WriteString("===============\n\n")
	fmt.Fprintf(&b, "dex files: %d\n", len(files))
	fmt.Fprintf(&b, "java files: %d\n", result.ClassCount)
	fmt.Fprintf(&b, "fields: %d\n", result.FieldCount)
	fmt.Fprintf(&b, "methods: %d\n", result.MethodCount)
	fmt.Fprintf(&b, "code methods: %d\n", result.CodeMethods)
	fmt.Fprintf(&b, "recovered simple methods: %d\n", result.RecoveredMethods)
	fmt.Fprintf(&b, "complex fallback methods: %d\n", result.ComplexMethods)
	fmt.Fprintf(&b, "no-code methods: %d\n", result.NoCodeMethods)
	fmt.Fprintf(&b, "decoded instructions: %d\n", result.InstructionCount)
	if result.SkippedCount > 0 {
		fmt.Fprintf(&b, "duplicates/problem paths adjusted: %d\n", result.SkippedCount)
	}
	b.WriteString("\nOutput: best-effort Java files with simple constructor including no-register Object init, constant return, field accessor, descriptor-preserving moves, descriptor-aware zero comparisons, overwritten-register numeric inference, boolean ternary argument coercion, typed arithmetic/bitwise results, descriptor-aware field/array assignment, owner-aware receiver recovery, branch-to-return fallback assignment recovery, nested goto assignment recovery, guarded void switch case recovery, degenerate ternary safety checks, null-safe throw rendering, unique local array temporaries, method-call return, new-instance return, simple arithmetic/compare, conditional assignment, conditional-assignment tail recovery, simple if/goto recovery, lazy-init return blocks, void if/goto blocks, static fill-array-data array literals, corrected wide invoke arguments, ignored-result invoke statements, binary-safe synthetic/inner type references, payload-aware instruction decoding, corrected 23x arithmetic opcode sizing, float/double constant coercion, recovered-body safety validation, corrected monitor opcode sizing, corrected 35c invoke/filled-new-array argument decoding, and frontend-friendly progress diagnostics. Complex methods include decoded Dalvik instruction comments.\n")
	return b.String()
}

func renderClass(cls ClassDef, opts JavaWriteOptions) string {
	pkg := packageName(cls.Descriptor)
	simple := simpleClassName(cls.Descriptor)
	var b strings.Builder
	b.WriteString("/*\n")
	b.WriteString(" * Generated by jadx-go.\n")
	b.WriteString(" * This is best-effort Java output: common method bodies may be recovered; complex bodies include decoded Dalvik comments.\n")
	b.WriteString(" * DEX: ")
	b.WriteString(cls.DexName)
	b.WriteString("\n")
	if cls.SourceFile != "" {
		b.WriteString(" * SourceFile: ")
		b.WriteString(cls.SourceFile)
		b.WriteString("\n")
	}
	b.WriteString(" */\n")
	if pkg != "" {
		b.WriteString("package ")
		b.WriteString(pkg)
		b.WriteString(";\n\n")
	}
	classLine := classDeclaration(cls, simple, opts)
	b.WriteString(classLine)
	b.WriteString(" {\n")

	if len(cls.StaticFields)+len(cls.Fields) > 0 {
		b.WriteByte('\n')
	}
	for _, field := range cls.StaticFields {
		b.WriteString("    ")
		b.WriteString(fieldDeclaration(field, true, opts))
		b.WriteByte('\n')
	}
	for _, field := range cls.Fields {
		b.WriteString("    ")
		b.WriteString(fieldDeclaration(field, false, opts))
		b.WriteByte('\n')
	}
	methods := append([]EncodedMethod{}, cls.Direct...)
	methods = append(methods, cls.Virtual...)
	if len(methods) > 0 {
		b.WriteByte('\n')
	}
	for i, method := range methods {
		b.WriteString(renderMethod(simple, method, opts))
		if i+1 < len(methods) {
			b.WriteByte('\n')
		}
	}
	b.WriteString("}\n")
	return b.String()
}

func classDeclaration(cls ClassDef, simple string, opts JavaWriteOptions) string {
	mods := classModifiers(cls.AccessFlags)
	kind := "class"
	if cls.AccessFlags&0x2000 != 0 {
		kind = "@interface"
	} else if cls.AccessFlags&0x4000 != 0 {
		kind = "enum"
	} else if cls.AccessFlags&0x0200 != 0 {
		kind = "interface"
	}
	parts := make([]string, 0, 8)
	parts = append(parts, mods...)
	parts = append(parts, kind, sanitizeIdentifier(simple))
	if kind == "class" || kind == "enum" {
		if cls.Superclass != "" && cls.Superclass != "Ljava/lang/Object;" {
			parts = append(parts, "extends", javaType(cls.Superclass, opts))
		}
	}
	interfaces := javaTypeList(cls.Interfaces, opts)
	if len(interfaces) > 0 && kind != "@interface" {
		if kind == "interface" {
			parts = append(parts, "extends")
		} else {
			parts = append(parts, "implements")
		}
		parts = append(parts, strings.Join(interfaces, ", "))
	}
	return strings.Join(parts, " ")
}

func fieldDeclaration(field EncodedField, forceStatic bool, opts JavaWriteOptions) string {
	mods := fieldModifiers(field.AccessFlags)
	if forceStatic && !contains(mods, "static") {
		mods = append(mods, "static")
	}
	parts := append([]string{}, mods...)
	parts = append(parts, javaType(field.ID.Type, opts), sanitizeIdentifier(field.ID.Name)+";")
	return strings.Join(parts, " ")
}

func renderMethod(className string, method EncodedMethod, opts JavaWriteOptions) string {
	name := method.ID.Name
	if name == "<clinit>" {
		return renderStaticInitializer(method, opts)
	}
	mods := methodModifiers(method.AccessFlags)
	var b strings.Builder
	b.WriteString("    ")
	if len(mods) > 0 {
		b.WriteString(strings.Join(mods, " "))
		b.WriteByte(' ')
	}
	constructor := name == "<init>"
	if constructor {
		b.WriteString(sanitizeIdentifier(className))
	} else {
		b.WriteString(javaType(method.ID.ReturnType, opts))
		b.WriteByte(' ')
		b.WriteString(sanitizeIdentifier(name))
	}
	b.WriteByte('(')
	params := make([]string, 0, len(method.ID.Parameters))
	for i, p := range method.ID.Parameters {
		params = append(params, fmt.Sprintf("%s p%d", javaType(p, opts), i))
	}
	b.WriteString(strings.Join(params, ", "))
	b.WriteString(")")
	if method.AccessFlags&0x0400 != 0 || method.AccessFlags&0x0100 != 0 {
		b.WriteString(";\n")
		return b.String()
	}
	b.WriteString(" {\n")
	writeMethodBody(&b, method, opts)
	b.WriteString("    }\n")
	return b.String()
}

func renderStaticInitializer(method EncodedMethod, opts JavaWriteOptions) string {
	var b strings.Builder
	b.WriteString("    static {\n")
	writeMethodBody(&b, method, opts)
	b.WriteString("    }\n")
	return b.String()
}

func writeMethodBody(b *strings.Builder, method EncodedMethod, opts JavaWriteOptions) {
	if opts.Stats != nil {
		if method.Code.HasCode {
			opts.Stats.CodeMethods++
			opts.Stats.InstructionCount += len(method.Code.Instructions)
		} else {
			opts.Stats.NoCodeMethods++
		}
	}
	if !method.Code.HasCode {
		b.WriteString("        // no code item present.\n")
		writeFallbackReturn(b, method, opts)
		return
	}
	fmt.Fprintf(b, "        // code_off=0x%x registers=%d ins=%d outs=%d tries=%d insns=%d\n",
		method.Code.Offset, method.Code.Registers, method.Code.Ins, method.Code.Outs, method.Code.Tries, method.Code.Insns)
	var simple strings.Builder
	if tryWriteSimpleJavaBody(&simple, method, opts) && recoveredJavaBodyLooksSafe(simple.String()) {
		b.WriteString(simple.String())
		if opts.Stats != nil {
			opts.Stats.RecoveredMethods++
		}
		return
	}
	if opts.Stats != nil {
		opts.Stats.ComplexMethods++
	}
	b.WriteString("        // jadx-go: complex method body; showing decoded Dalvik instructions for now.\n")
	writeInstructionComments(b, method.Code.Instructions)
	writeFallbackReturn(b, method, opts)
}

func tryWriteSimpleJavaBody(b *strings.Builder, method EncodedMethod, opts JavaWriteOptions) bool {
	ins := meaningfulInstructions(method.Code.Instructions)
	if len(ins) == 0 {
		return false
	}
	ret := javaType(method.ID.ReturnType, opts)
	isCtor := method.ID.Name == "<init>" || method.ID.Name == "<clinit>"
	if len(ins) == 1 && ins[0].Name == "return-void" {
		b.WriteString("        // jadx-go: decompiled simple empty method.\n")
		return true
	}
	if tryWriteConstructorBody(b, method, ins, opts) {
		return true
	}
	if tryWriteSimpleTryCatchReturnBody(b, method, ins, opts) {
		return true
	}
	if tryWriteSimpleSwitchBody(b, method, ins, opts) {
		return true
	}
	if tryWriteIfTerminalElseTerminalBody(b, method, ins, opts) {
		return true
	}
	if tryWriteMultiBranchAssignmentThenLinearBody(b, method, ins, opts) {
		return true
	}
	if tryWriteConditionalAssignmentThenLinearBody(b, method, ins, opts) {
		return true
	}
	if tryWriteIfGotoReturnBody(b, method, ins, opts) {
		return true
	}
	if tryWriteIfThenInitReturnBody(b, method, ins, opts) {
		return true
	}
	if tryWriteIfSkipVoidBody(b, method, ins, opts) {
		return true
	}
	if tryWriteIfGotoVoidBody(b, method, ins, opts) {
		return true
	}
	if tryWriteAssignedConditionalReturnBody(b, method, ins, opts) {
		return true
	}
	if tryWriteIfFallthroughAssignReturnBody(b, method, ins, opts) {
		return true
	}
	if tryWriteGuardedReturnBody(b, method, ins, opts) {
		return true
	}
	if tryWriteSynchronizedBody(b, method, ins, opts) {
		return true
	}
	if tryWriteSimpleIfReturnBody(b, method, ins, opts) {
		return true
	}
	consts := map[int]string{}
	for _, in := range ins {
		switch in.Name {
		case "const/4", "const/16", "const", "const/high16", "const-wide/16", "const-wide/32", "const-wide", "const-wide/high16":
			if len(in.Registers) > 0 && in.IntLiteral != nil {
				consts[in.Registers[0]] = javaLiteralForReturn(*in.IntLiteral, ret)
			}
		case "const-string", "const-string/jumbo":
			if len(in.Registers) > 0 {
				consts[in.Registers[0]] = quoteJava(in.StringLiteral)
			}
		}
	}
	if len(ins) == 2 && isReturnWithRegister(ins[1]) {
		if expr, ok := consts[ins[1].Registers[0]]; ok {
			b.WriteString("        // jadx-go: decompiled simple constant return.\n")
			fmt.Fprintf(b, "        return %s;\n", expr)
			return true
		}
	}
	if len(ins) == 2 && isFieldGet(ins[0]) && isReturnWithRegister(ins[1]) && sameFirstRegister(ins[0], ins[1]) {
		if expr := fieldReadExpression(method, ins[0], opts); expr != "" {
			b.WriteString("        // jadx-go: decompiled simple field getter.\n")
			fmt.Fprintf(b, "        return %s;\n", expr)
			return true
		}
	}
	if ret == "void" && !isCtor && len(ins) == 2 && isFieldPut(ins[0]) && ins[1].Name == "return-void" {
		if stmt := fieldWriteStatement(method, ins[0], opts); stmt != "" {
			b.WriteString("        // jadx-go: decompiled simple field setter.\n")
			fmt.Fprintf(b, "        %s;\n", stmt)
			return true
		}
	}
	return tryWriteLinearJavaBody(b, method, ins, opts)
}

func tryWriteConstructorBody(b *strings.Builder, method EncodedMethod, ins []Instruction, opts JavaWriteOptions) bool {
	if method.ID.Name != "<init>" || len(ins) < 2 || ins[len(ins)-1].Name != "return-void" {
		return false
	}
	initIndex := -1
	for i, item := range ins[:len(ins)-1] {
		if strings.HasPrefix(item.Name, "invoke-direct") && item.MethodRef.Name == "<init>" && constructorInitTargetsThis(method, item) {
			initIndex = i
			break
		}
	}
	if initIndex < 0 {
		return false
	}
	initCall := ins[initIndex]
	values := map[int]string{}
	movedPreStatements := []string{}
	if initIndex > 0 {
		pre, ok := evaluateLinearInstructions(method, ins[:initIndex], opts, values, false)
		if !ok || pre.returns {
			return false
		}
		// Synthetic and Kotlin constructors often assign captured fields before the
		// bytecode-level Object/Lambda constructor call. Java source requires the
		// this()/super() call first, so keep the values for constructor arguments and
		// move safe field writes after it.
		movedPreStatements = append(movedPreStatements, pre.statements...)
	}
	callName := "super"
	if initCall.MethodRef.Class == method.ID.Class {
		callName = "this"
	}
	argsRegs := []int{}
	if len(initCall.Registers) > 1 {
		argsRegs = initCall.Registers[1:]
	}
	args := argumentExpressions(method, argsRegs, initCall.MethodRef.Parameters, values)
	block, ok := evaluateLinearInstructions(method, ins[initIndex+1:], opts, values, true)
	if !ok || !block.returns || block.expr != "" {
		return false
	}
	b.WriteString("        // jadx-go: decompiled simple constructor body.\n")
	fmt.Fprintf(b, "        %s(%s);\n", callName, strings.Join(args, ", "))
	for _, stmt := range movedPreStatements {
		fmt.Fprintf(b, "        %s\n", stmt)
	}
	for _, stmt := range block.statements {
		fmt.Fprintf(b, "        %s\n", stmt)
	}
	return true
}

func constructorInitTargetsThis(method EncodedMethod, item Instruction) bool {
	if len(item.Registers) > 0 {
		return registerExpression(method, item.Registers[0]) == "this"
	}
	return item.MethodRef.Class == "Ljava/lang/Object;" && len(item.MethodRef.Parameters) == 0
}

func tryWriteSimpleSwitchBody(b *strings.Builder, method EncodedMethod, ins []Instruction, opts JavaWriteOptions) bool {
	if method.ID.Name == "<init>" || method.ID.Name == "<clinit>" {
		return false
	}
	ret := javaType(method.ID.ReturnType, opts)
	offsetIndex := instructionOffsetIndex(ins)
	for i, item := range ins {
		if item.Name != "packed-switch" && item.Name != "sparse-switch" {
			continue
		}
		if len(item.Registers) == 0 || len(item.SwitchKeys) == 0 || len(item.SwitchKeys) != len(item.SwitchTargets) {
			continue
		}
		values, prefixStatements, ok := evaluateSimplePrefix(method, ins[:i], opts)
		if !ok {
			continue
		}
		switchExpr := valueExpression(method, values, item.Registers[0])
		if switchExpr == "" {
			continue
		}
		defaultBlock, ok := evaluateTerminalSliceFrom(method, ins, i+1, opts, cloneValues(values))
		if !ok {
			continue
		}
		caseBlocks := make([]simpleBlockResult, 0, len(item.SwitchKeys))
		caseOK := true
		for _, target := range item.SwitchTargets {
			start, ok := offsetIndex[uint32(target)]
			if !ok || start < 0 || start >= len(ins) {
				caseOK = false
				break
			}
			block, ok := evaluateTerminalSliceFrom(method, ins, start, opts, cloneValues(values))
			if !ok {
				caseOK = false
				break
			}
			caseBlocks = append(caseBlocks, block)
		}
		if !caseOK || len(caseBlocks) != len(item.SwitchKeys) {
			continue
		}
		b.WriteString("        // jadx-go: decompiled simple switch body.\n")
		for _, stmt := range prefixStatements {
			fmt.Fprintf(b, "        %s\n", stmt)
		}
		fmt.Fprintf(b, "        switch (%s) {\n", switchExpr)
		for c, key := range item.SwitchKeys {
			fmt.Fprintf(b, "            case %d:\n", key)
			writeSimpleBlockAsSwitchCase(b, caseBlocks[c], ret, "                ")
		}
		b.WriteString("            default:\n")
		writeSimpleBlockAsSwitchCase(b, defaultBlock, ret, "                ")
		b.WriteString("        }\n")
		return true
	}
	return false
}

func tryWriteIfTerminalElseTerminalBody(b *strings.Builder, method EncodedMethod, ins []Instruction, opts JavaWriteOptions) bool {
	if method.ID.Name == "<init>" || method.ID.Name == "<clinit>" || len(ins) < 4 {
		return false
	}
	ret := javaType(method.ID.ReturnType, opts)
	offsetIndex := instructionOffsetIndex(ins)
	for i, item := range ins {
		if !isIfInstruction(item) || item.Target <= int32(item.Offset) {
			continue
		}
		targetIndex, ok := offsetIndex[uint32(item.Target)]
		if !ok || targetIndex <= i+1 || targetIndex >= len(ins) {
			continue
		}
		values, prefixStatements, ok := evaluateSimplePrefix(method, ins[:i], opts)
		if !ok {
			continue
		}
		cond := conditionExpression(method, item, opts, values)
		if cond == "" {
			continue
		}
		fallBlock, fallEnd, ok := evaluateTerminalSliceFromWithEnd(method, ins, i+1, opts, cloneValues(values))
		if !ok || fallEnd > targetIndex {
			// The fall-through side must terminate before the branch target, otherwise
			// this is normal structured control flow that needs a fuller CFG pass.
			continue
		}
		targetBlock, ok := evaluateTerminalSliceFrom(method, ins, targetIndex, opts, cloneValues(values))
		if !ok {
			continue
		}
		b.WriteString("        // jadx-go: decompiled simple if/else terminal body.\n")
		for _, stmt := range prefixStatements {
			fmt.Fprintf(b, "        %s\n", stmt)
		}
		fmt.Fprintf(b, "        if (%s) {\n", cond)
		writeSimpleBlockStatements(b, targetBlock, ret, "            ")
		b.WriteString("        }\n")
		writeSimpleBlockStatements(b, fallBlock, ret, "        ")
		return true
	}
	return false
}

func tryWriteMultiBranchAssignmentThenLinearBody(b *strings.Builder, method EncodedMethod, ins []Instruction, opts JavaWriteOptions) bool {
	if method.ID.Name == "<init>" || method.ID.Name == "<clinit>" || len(ins) < 6 {
		return false
	}
	ret := javaType(method.ID.ReturnType, opts)
	offsetIndex := instructionOffsetIndex(ins)
	for i, item := range ins {
		if !isIfInstruction(item) || item.Target <= int32(item.Offset) {
			continue
		}
		targetIndex, ok := offsetIndex[uint32(item.Target)]
		if !ok || targetIndex <= i+1 || targetIndex >= len(ins) {
			continue
		}
		joinCandidates := assignmentJoinCandidates(ins, i+1, len(ins), targetIndex)
		for _, joinIndex := range joinCandidates {
			if joinIndex <= targetIndex || joinIndex >= len(ins) {
				continue
			}
			values, prefixStatements, ok := evaluateSimplePrefix(method, ins[:i], opts)
			if !ok {
				continue
			}
			reg, expr, desc, ok := parseConditionalAssignmentChoice(method, ins, i, joinIndex, opts, cloneValues(values), 0)
			if !ok || reg < 0 || expr == "" {
				continue
			}
			if desc != "" {
				values[reg] = typedExpression(expr, desc)
			} else {
				values[reg] = expr
			}
			tail, ok := evaluateLinearInstructions(method, ins[joinIndex:], opts, values, true)
			if !ok {
				if ret != "void" {
					continue
				}
				voidTail, ok := evaluateVoidTailWithOptionalGuard(method, ins[joinIndex:], opts, values)
				if !ok {
					continue
				}
				b.WriteString("        // jadx-go: decompiled multi-branch assignment body.\n")
				for _, stmt := range prefixStatements {
					fmt.Fprintf(b, "        %s\n", stmt)
				}
				for _, stmt := range voidTail.statements {
					fmt.Fprintf(b, "        %s\n", stmt)
				}
				return true
			}
			if !tail.returns {
				continue
			}
			b.WriteString("        // jadx-go: decompiled multi-branch assignment body.\n")
			for _, stmt := range prefixStatements {
				fmt.Fprintf(b, "        %s\n", stmt)
			}
			for _, stmt := range tail.statements {
				fmt.Fprintf(b, "        %s\n", stmt)
			}
			if tail.expr != "" && ret != "void" {
				fmt.Fprintf(b, "        return %s;\n", returnExpressionForType(tail.expr, ret))
			}
			return true
		}
	}
	return false
}

func assignmentJoinCandidates(ins []Instruction, start int, end int, branchTarget int) []int {
	seen := map[int]bool{}
	out := []int{}
	if end > len(ins) {
		end = len(ins)
	}
	for i := start; i < end; i++ {
		if isGotoInstruction(ins[i]) && ins[i].Target > int32(ins[i].Offset) {
			for j := branchTarget + 1; j < end; j++ {
				if uint32(ins[i].Target) == ins[j].Offset && !seen[j] {
					seen[j] = true
					out = append(out, j)
				}
			}
		}
	}
	return out
}

func parseConditionalAssignmentChoice(method EncodedMethod, ins []Instruction, start int, join int, opts JavaWriteOptions, values map[int]string, depth int) (int, string, string, bool) {
	if depth > 4 || start < 0 || start >= join || join > len(ins) {
		return -1, "", "", false
	}
	item := ins[start]
	if isIfInstruction(item) {
		offsetIndex := instructionOffsetIndex(ins)
		targetIndex, ok := offsetIndex[uint32(item.Target)]
		if !ok || targetIndex <= start+1 || targetIndex >= join {
			return -1, "", "", false
		}
		cond := conditionExpression(method, item, opts, values)
		if cond == "" {
			return -1, "", "", false
		}
		fallReg, fallExpr, fallDesc, ok := parseConditionalAssignmentChoice(method, ins, start+1, join, opts, cloneValues(values), depth+1)
		if !ok {
			fallReg, fallExpr, fallDesc, ok = evaluateAssignmentPathToJoin(method, ins, start+1, targetIndex, join, opts, cloneValues(values))
		}
		if !ok {
			return -1, "", "", false
		}
		targetReg, targetExpr, targetDesc, ok := parseConditionalAssignmentChoice(method, ins, targetIndex, join, opts, cloneValues(values), depth+1)
		if !ok {
			targetReg, targetExpr, targetDesc, ok = evaluateAssignmentPathToJoin(method, ins, targetIndex, join, join, opts, cloneValues(values))
		}
		if !ok || fallReg != targetReg {
			return -1, "", "", false
		}
		desc := targetDesc
		if desc == "" {
			desc = fallDesc
		}
		expr := "(" + cond + " ? " + targetExpr + " : " + fallExpr + ")"
		if desc != "" {
			expr = typedExpression(expr, desc)
		}
		return fallReg, expressionText(expr), desc, true
	}
	return evaluateAssignmentPathToJoin(method, ins, start, join, join, opts, values)
}

func evaluateAssignmentPathToJoin(method EncodedMethod, ins []Instruction, start int, end int, join int, opts JavaWriteOptions, values map[int]string) (int, string, string, bool) {
	return evaluateAssignmentPathToJoinDepth(method, ins, start, end, join, opts, values, 0)
}

func evaluateAssignmentPathToJoinDepth(method EncodedMethod, ins []Instruction, start int, end int, join int, opts JavaWriteOptions, values map[int]string, depth int) (int, string, string, bool) {
	if depth > 4 || start < 0 || start >= end || end > len(ins) || join > len(ins) {
		return -1, "", "", false
	}
	stop := end
	if stop > join {
		stop = join
	}
	offsetIndex := instructionOffsetIndex(ins)
	for i := start; i < stop; i++ {
		if isGotoInstruction(ins[i]) {
			if uint32(ins[i].Target) == ins[join].Offset {
				stop = i
				break
			}
			targetIndex, ok := offsetIndex[uint32(ins[i].Target)]
			if !ok || targetIndex <= i || targetIndex >= join {
				return -1, "", "", false
			}
			prefixValues := cloneValues(values)
			reg, expr, desc, ok := evaluateSingleAssignmentBlock(method, ins[start:i], opts, prefixValues)
			if ok {
				return reg, expr, desc, true
			}
			return evaluateAssignmentPathToJoinDepth(method, ins, targetIndex, join, join, opts, values, depth+1)
		}
	}
	reg, expr, desc, ok := evaluateSingleAssignmentBlock(method, ins[start:stop], opts, values)
	if !ok {
		return -1, "", "", false
	}
	return reg, expr, desc, true
}

func evaluateVoidTailWithOptionalGuard(method EncodedMethod, ins []Instruction, opts JavaWriteOptions, values map[int]string) (simpleBlockResult, bool) {
	var out simpleBlockResult
	if len(ins) < 3 || ins[len(ins)-1].Name != "return-void" {
		return out, false
	}
	finalOffset := int32(ins[len(ins)-1].Offset)
	guardIndex := -1
	for i, item := range ins[:len(ins)-1] {
		if isIfInstruction(item) && item.Target == finalOffset {
			guardIndex = i
			break
		}
	}
	if guardIndex < 0 {
		return out, false
	}
	prefix, ok := evaluateLinearInstructions(method, ins[:guardIndex], opts, values, false)
	if !ok || prefix.returns {
		return out, false
	}
	out.statements = append(out.statements, prefix.statements...)
	cond := conditionExpression(method, ins[guardIndex], opts, values)
	if cond == "" {
		return out, false
	}
	fallValues := cloneValues(values)
	fall, ok := evaluateLinearInstructions(method, ins[guardIndex+1:len(ins)-1], opts, fallValues, false)
	if !ok || fall.returns {
		return out, false
	}
	out.statements = append(out.statements, "if ("+cond+") {")
	out.statements = append(out.statements, "    return;")
	out.statements = append(out.statements, "}")
	out.statements = append(out.statements, fall.statements...)
	out.returns = true
	return out, true
}

func tryWriteConditionalAssignmentThenLinearBody(b *strings.Builder, method EncodedMethod, ins []Instruction, opts JavaWriteOptions) bool {
	if method.ID.Name == "<init>" || method.ID.Name == "<clinit>" || len(ins) < 6 {
		return false
	}
	ret := javaType(method.ID.ReturnType, opts)
	offsetIndex := instructionOffsetIndex(ins)
	for i, item := range ins {
		if !isIfInstruction(item) || item.Target <= int32(item.Offset) {
			continue
		}
		targetIndex, ok := offsetIndex[uint32(item.Target)]
		if !ok || targetIndex <= i+1 || targetIndex >= len(ins) {
			continue
		}
		gotoIndex := -1
		for j := i + 1; j < targetIndex; j++ {
			if isGotoInstruction(ins[j]) && ins[j].Target > int32(item.Target) {
				gotoIndex = j
				break
			}
		}
		if gotoIndex < 0 {
			continue
		}
		joinIndex, ok := offsetIndex[uint32(ins[gotoIndex].Target)]
		if !ok || joinIndex <= targetIndex || joinIndex >= len(ins) {
			continue
		}
		values, prefixStatements, ok := evaluateSimplePrefix(method, ins[:i], opts)
		if !ok {
			continue
		}
		cond := conditionExpression(method, item, opts, values)
		if cond == "" {
			continue
		}
		fallReg, fallExpr, fallDesc, ok := evaluateSingleAssignmentBlock(method, ins[i+1:gotoIndex], opts, cloneValues(values))
		if !ok {
			continue
		}
		targetReg, targetExpr, targetDesc, ok := evaluateSingleAssignmentBlock(method, ins[targetIndex:joinIndex], opts, cloneValues(values))
		if !ok || fallReg != targetReg {
			continue
		}
		desc := targetDesc
		if desc == "" {
			desc = fallDesc
		}
		ternary := "(" + cond + " ? " + targetExpr + " : " + fallExpr + ")"
		if desc != "" {
			ternary = typedExpression(ternary, desc)
		}
		values[targetReg] = ternary
		tail, ok := evaluateLinearInstructions(method, ins[joinIndex:], opts, values, true)
		if !ok || !tail.returns {
			continue
		}
		b.WriteString("        // jadx-go: decompiled simple conditional assignment body.\n")
		for _, stmt := range prefixStatements {
			fmt.Fprintf(b, "        %s\n", stmt)
		}
		for _, stmt := range tail.statements {
			fmt.Fprintf(b, "        %s\n", stmt)
		}
		if tail.expr != "" && ret != "void" {
			fmt.Fprintf(b, "        return %s;\n", returnExpressionForType(tail.expr, ret))
		} else if ret == "void" && len(tail.statements) > 0 && !strings.HasPrefix(strings.TrimSpace(tail.statements[len(tail.statements)-1]), "throw ") {
			// evaluateLinearInstructions consumes the terminal return-void; no extra line is needed
		}
		return true
	}
	return false
}

func evaluateSingleAssignmentBlock(method EncodedMethod, ins []Instruction, opts JavaWriteOptions, values map[int]string) (int, string, string, bool) {
	before := cloneValues(values)
	block, ok := evaluateLinearInstructions(method, ins, opts, values, false)
	if !ok || block.returns || len(block.statements) != 0 {
		return 0, "", "", false
	}
	changedReg := -1
	changedExpr := ""
	changedDesc := ""
	for reg, expr := range values {
		if beforeExpr, existed := before[reg]; existed && beforeExpr == expr {
			continue
		}
		if changedReg >= 0 {
			return 0, "", "", false
		}
		changedReg = reg
		changedExpr = expressionText(expr)
		changedDesc = expressionDescriptor(expr)
	}
	if changedReg < 0 || changedExpr == "" {
		return 0, "", "", false
	}
	return changedReg, changedExpr, changedDesc, true
}

func evaluateTerminalSliceFrom(method EncodedMethod, ins []Instruction, start int, opts JavaWriteOptions, values map[int]string) (simpleBlockResult, bool) {
	block, _, ok := evaluateTerminalSliceFromWithEnd(method, ins, start, opts, values)
	return block, ok
}

func evaluateTerminalSliceFromWithEnd(method EncodedMethod, ins []Instruction, start int, opts JavaWriteOptions, values map[int]string) (simpleBlockResult, int, bool) {
	var empty simpleBlockResult
	if start < 0 || start >= len(ins) || isPayloadInstruction(ins[start]) {
		return empty, start, false
	}
	end := start
	for end < len(ins) {
		if isPayloadInstruction(ins[end]) {
			break
		}
		if isTerminalInstruction(ins[end]) {
			end++
			break
		}
		end++
	}
	if end <= start || end > len(ins) {
		return empty, end, false
	}
	block, ok := evaluateLinearInstructions(method, ins[start:end], opts, values, true)
	if ok {
		return block, end, true
	}
	if javaType(method.ID.ReturnType, opts) == "void" {
		block, ok = evaluateGuardedVoidBlock(method, ins[start:end], opts, values)
		if ok {
			return block, end, true
		}
	}
	return block, end, false
}

func evaluateGuardedVoidBlock(method EncodedMethod, ins []Instruction, opts JavaWriteOptions, values map[int]string) (simpleBlockResult, bool) {
	var out simpleBlockResult
	if len(ins) == 0 || ins[len(ins)-1].Name != "return-void" {
		return out, false
	}
	if values == nil {
		values = map[int]string{}
	}
	finalOffset := int32(ins[len(ins)-1].Offset)
	idx := 0
	for idx < len(ins)-1 {
		nextGuard := -1
		for i := idx; i < len(ins)-1; i++ {
			if isIfInstruction(ins[i]) {
				if ins[i].Target != finalOffset {
					return out, false
				}
				nextGuard = i
				break
			}
		}
		end := len(ins) - 1
		if nextGuard >= 0 {
			end = nextGuard
		}
		if end > idx {
			chunk, ok := evaluateLinearInstructions(method, ins[idx:end], opts, values, false)
			if !ok || chunk.returns {
				return out, false
			}
			out.statements = append(out.statements, chunk.statements...)
		}
		if nextGuard < 0 {
			break
		}
		cond := conditionExpression(method, ins[nextGuard], opts, values)
		if cond == "" {
			return out, false
		}
		out.statements = append(out.statements, "if ("+cond+") {")
		out.statements = append(out.statements, "    return;")
		out.statements = append(out.statements, "}")
		idx = nextGuard + 1
	}
	out.returns = true
	return out, true
}

func isTerminalInstruction(in Instruction) bool {
	return in.Name == "return-void" || in.Name == "throw" || isReturnWithRegister(in)
}

func writeSimpleBlockAsSwitchCase(b *strings.Builder, block simpleBlockResult, ret string, indent string) {
	writeSimpleBlockStatements(b, block, ret, indent)
}

func writeSimpleBlockStatements(b *strings.Builder, block simpleBlockResult, ret string, indent string) {
	for _, stmt := range block.statements {
		fmt.Fprintf(b, "%s%s\n", indent, stmt)
	}
	if block.expr != "" && ret != "void" {
		fmt.Fprintf(b, "%sreturn %s;\n", indent, returnExpressionForType(block.expr, ret))
		return
	}
	if block.returns && ret == "void" {
		if len(block.statements) > 0 && strings.HasPrefix(strings.TrimSpace(block.statements[len(block.statements)-1]), "throw ") {
			return
		}
		fmt.Fprintf(b, "%sreturn;\n", indent)
	}
}

func tryWriteAssignedConditionalReturnBody(b *strings.Builder, method EncodedMethod, ins []Instruction, opts JavaWriteOptions) bool {
	ret := javaType(method.ID.ReturnType, opts)
	if ret == "void" || method.ID.Name == "<init>" || method.ID.Name == "<clinit>" || len(ins) < 4 {
		return false
	}
	offsetIndex := instructionOffsetIndex(ins)
	for i, item := range ins {
		if !isIfInstruction(item) || item.Target <= int32(item.Offset) {
			continue
		}
		targetIndex, ok := offsetIndex[uint32(item.Target)]
		if !ok || targetIndex <= i+1 || targetIndex >= len(ins) {
			continue
		}
		if !isReturnWithRegister(ins[targetIndex]) || len(ins[targetIndex].Registers) == 0 {
			continue
		}
		returnReg := ins[targetIndex].Registers[0]
		values, prefixStatements, ok := evaluateSimplePrefix(method, ins[:i], opts)
		if !ok {
			continue
		}
		cond := conditionExpression(method, item, opts, values)
		if cond == "" {
			continue
		}
		targetExpr := returnExpressionForType(valueExpression(method, values, returnReg), ret)
		fallValues := cloneValues(values)
		fallBlock, ok := evaluateLinearInstructions(method, ins[i+1:targetIndex], opts, fallValues, false)
		if !ok || fallBlock.returns {
			continue
		}
		fallExpr := returnExpressionForType(valueExpression(method, fallValues, returnReg), ret)
		if targetExpr == fallExpr && len(fallBlock.statements) == 0 {
			continue
		}
		b.WriteString("        // jadx-go: decompiled conditional assigned return.\n")
		for _, stmt := range prefixStatements {
			fmt.Fprintf(b, "        %s\n", stmt)
		}
		fmt.Fprintf(b, "        if (%s) {\n", cond)
		fmt.Fprintf(b, "            return %s;\n", targetExpr)
		b.WriteString("        }\n")
		for _, stmt := range fallBlock.statements {
			fmt.Fprintf(b, "        %s\n", stmt)
		}
		fmt.Fprintf(b, "        return %s;\n", fallExpr)
		return true
	}
	return false
}

func tryWriteIfFallthroughAssignReturnBody(b *strings.Builder, method EncodedMethod, ins []Instruction, opts JavaWriteOptions) bool {
	ret := javaType(method.ID.ReturnType, opts)
	if ret == "void" || method.ID.Name == "<init>" || method.ID.Name == "<clinit>" || len(ins) < 3 {
		return false
	}
	offsetIndex := instructionOffsetIndex(ins)
	for i, item := range ins {
		if !isIfInstruction(item) || item.Target <= int32(item.Offset) {
			continue
		}
		targetIndex, ok := offsetIndex[uint32(item.Target)]
		if !ok || targetIndex <= i+1 || targetIndex >= len(ins) {
			continue
		}
		values, prefixStatements, ok := evaluateSimplePrefix(method, ins[:i], opts)
		if !ok {
			continue
		}
		cond := conditionExpression(method, item, opts, values)
		if cond == "" {
			continue
		}
		targetValues := cloneValues(values)
		targetBlock, ok := evaluateLinearInstructions(method, ins[targetIndex:], opts, targetValues, true)
		if !ok || !targetBlock.returns || targetBlock.expr == "" {
			continue
		}
		fallValues := cloneValues(values)
		fallBlock, ok := evaluateLinearInstructions(method, ins[i+1:targetIndex], opts, fallValues, false)
		if !ok || fallBlock.returns {
			continue
		}
		fallTail, ok := evaluateLinearInstructions(method, ins[targetIndex:], opts, fallValues, true)
		if !ok || !fallTail.returns || fallTail.expr == "" {
			continue
		}
		targetExpr := returnExpressionForType(targetBlock.expr, ret)
		fallExpr := returnExpressionForType(fallTail.expr, ret)
		b.WriteString("        // jadx-go: decompiled simple branch-to-return assignment body.\n")
		for _, stmt := range prefixStatements {
			fmt.Fprintf(b, "        %s\n", stmt)
		}
		if len(targetBlock.statements) == 0 && len(fallBlock.statements) == 0 && len(fallTail.statements) == 0 {
			fmt.Fprintf(b, "        return (%s ? %s : %s);\n", cond, targetExpr, fallExpr)
			return true
		}
		fmt.Fprintf(b, "        if (%s) {\n", cond)
		for _, stmt := range targetBlock.statements {
			fmt.Fprintf(b, "            %s\n", stmt)
		}
		fmt.Fprintf(b, "            return %s;\n", targetExpr)
		b.WriteString("        }\n")
		for _, stmt := range fallBlock.statements {
			fmt.Fprintf(b, "        %s\n", stmt)
		}
		for _, stmt := range fallTail.statements {
			fmt.Fprintf(b, "        %s\n", stmt)
		}
		fmt.Fprintf(b, "        return %s;\n", fallExpr)
		return true
	}
	return false
}

func tryWriteIfGotoReturnBody(b *strings.Builder, method EncodedMethod, ins []Instruction, opts JavaWriteOptions) bool {
	ret := javaType(method.ID.ReturnType, opts)
	if ret == "void" || method.ID.Name == "<init>" || method.ID.Name == "<clinit>" || len(ins) < 5 {
		return false
	}
	offsetIndex := instructionOffsetIndex(ins)
	for i, item := range ins {
		if !isIfInstruction(item) || item.Target <= int32(item.Offset) {
			continue
		}
		targetIndex, ok := offsetIndex[uint32(item.Target)]
		if !ok || targetIndex <= i+1 || targetIndex >= len(ins)-1 {
			continue
		}
		gotoIndex := -1
		for j := i + 1; j < targetIndex; j++ {
			if isGotoInstruction(ins[j]) && ins[j].Target > int32(ins[j].Offset) {
				gotoIndex = j
				break
			}
		}
		if gotoIndex < 0 {
			continue
		}
		joinIndex, ok := offsetIndex[uint32(ins[gotoIndex].Target)]
		if !ok || joinIndex <= targetIndex || joinIndex >= len(ins) || !isReturnWithRegister(ins[joinIndex]) || len(ins[joinIndex].Registers) == 0 {
			continue
		}
		returnReg := ins[joinIndex].Registers[0]
		values, prefixStatements, ok := evaluateSimplePrefix(method, ins[:i], opts)
		if !ok {
			continue
		}
		cond := conditionExpression(method, item, opts, values)
		if cond == "" {
			continue
		}
		fallValues := cloneValues(values)
		fallBlock, ok := evaluateLinearInstructions(method, ins[i+1:gotoIndex], opts, fallValues, false)
		if !ok || fallBlock.returns {
			continue
		}
		targetValues := cloneValues(values)
		targetBlock, ok := evaluateLinearInstructions(method, ins[targetIndex:joinIndex], opts, targetValues, false)
		if !ok || targetBlock.returns {
			continue
		}
		fallExpr := returnExpressionForType(valueExpression(method, fallValues, returnReg), ret)
		targetExpr := returnExpressionForType(valueExpression(method, targetValues, returnReg), ret)
		b.WriteString("        // jadx-go: decompiled simple if/goto return body.\n")
		for _, stmt := range prefixStatements {
			fmt.Fprintf(b, "        %s\n", stmt)
		}
		fmt.Fprintf(b, "        if (%s) {\n", cond)
		for _, stmt := range targetBlock.statements {
			fmt.Fprintf(b, "            %s\n", stmt)
		}
		fmt.Fprintf(b, "            return %s;\n", targetExpr)
		b.WriteString("        }\n")
		for _, stmt := range fallBlock.statements {
			fmt.Fprintf(b, "        %s\n", stmt)
		}
		fmt.Fprintf(b, "        return %s;\n", fallExpr)
		return true
	}
	return false
}

func tryWriteIfThenInitReturnBody(b *strings.Builder, method EncodedMethod, ins []Instruction, opts JavaWriteOptions) bool {
	ret := javaType(method.ID.ReturnType, opts)
	if ret == "void" || method.ID.Name == "<init>" || method.ID.Name == "<clinit>" || len(ins) < 5 {
		return false
	}
	offsetIndex := instructionOffsetIndex(ins)
	for i, item := range ins {
		if !isIfInstruction(item) || item.Target <= int32(item.Offset) {
			continue
		}
		targetIndex, ok := offsetIndex[uint32(item.Target)]
		if !ok || targetIndex <= i+1 || targetIndex >= len(ins) {
			continue
		}
		values, prefixStatements, ok := evaluateSimplePrefix(method, ins[:i], opts)
		if !ok {
			continue
		}
		cond := conditionExpression(method, item, opts, values)
		if cond == "" {
			continue
		}
		targetValues := cloneValues(values)
		targetBlock, ok := evaluateLinearInstructions(method, ins[targetIndex:], opts, targetValues, true)
		if !ok || !targetBlock.returns || targetBlock.expr == "" {
			continue
		}
		fallValues := cloneValues(values)
		fallBlock, ok := evaluateLinearInstructions(method, ins[i+1:targetIndex], opts, fallValues, false)
		if !ok || fallBlock.returns {
			continue
		}
		fallReturn, ok := evaluateLinearInstructions(method, ins[targetIndex:], opts, fallValues, true)
		if !ok || !fallReturn.returns || fallReturn.expr == "" {
			continue
		}
		b.WriteString("        // jadx-go: decompiled simple if/init return body.\n")
		for _, stmt := range prefixStatements {
			fmt.Fprintf(b, "        %s\n", stmt)
		}
		fmt.Fprintf(b, "        if (%s) {\n", cond)
		for _, stmt := range targetBlock.statements {
			fmt.Fprintf(b, "            %s\n", stmt)
		}
		fmt.Fprintf(b, "            return %s;\n", returnExpressionForType(targetBlock.expr, ret))
		b.WriteString("        }\n")
		for _, stmt := range fallBlock.statements {
			fmt.Fprintf(b, "        %s\n", stmt)
		}
		for _, stmt := range fallReturn.statements {
			fmt.Fprintf(b, "        %s\n", stmt)
		}
		fmt.Fprintf(b, "        return %s;\n", returnExpressionForType(fallReturn.expr, ret))
		return true
	}
	return false
}

func tryWriteIfSkipVoidBody(b *strings.Builder, method EncodedMethod, ins []Instruction, opts JavaWriteOptions) bool {
	ret := javaType(method.ID.ReturnType, opts)
	if ret != "void" || method.ID.Name == "<init>" || method.ID.Name == "<clinit>" || len(ins) < 3 {
		return false
	}
	offsetIndex := instructionOffsetIndex(ins)
	for i, item := range ins {
		if !isIfInstruction(item) || item.Target <= int32(item.Offset) {
			continue
		}
		targetIndex, ok := offsetIndex[uint32(item.Target)]
		if !ok || targetIndex <= i+1 || targetIndex >= len(ins) || ins[targetIndex].Name != "return-void" {
			continue
		}
		// Only handle the simple guard/skip shape for now: prefix, if -> final
		// return, straight-line work, final return. This covers many listener and
		// one-shot setup methods without pretending to solve loops/general CFG.
		if targetIndex != len(ins)-1 {
			continue
		}
		values, prefixStatements, ok := evaluateSimplePrefix(method, ins[:i], opts)
		if !ok {
			continue
		}
		cond := conditionExpression(method, item, opts, values)
		if cond == "" {
			continue
		}
		fallValues := cloneValues(values)
		fallBlock, ok := evaluateLinearInstructions(method, ins[i+1:targetIndex], opts, fallValues, false)
		if !ok || fallBlock.returns {
			continue
		}
		b.WriteString("        // jadx-go: decompiled simple void guard body.\n")
		for _, stmt := range prefixStatements {
			fmt.Fprintf(b, "        %s\n", stmt)
		}
		fmt.Fprintf(b, "        if (%s) {\n", cond)
		b.WriteString("            return;\n")
		b.WriteString("        }\n")
		for _, stmt := range fallBlock.statements {
			fmt.Fprintf(b, "        %s\n", stmt)
		}
		return true
	}
	return false
}

func tryWriteIfGotoVoidBody(b *strings.Builder, method EncodedMethod, ins []Instruction, opts JavaWriteOptions) bool {
	ret := javaType(method.ID.ReturnType, opts)
	if ret != "void" || method.ID.Name == "<init>" || method.ID.Name == "<clinit>" || len(ins) < 5 {
		return false
	}
	offsetIndex := instructionOffsetIndex(ins)
	for i, item := range ins {
		if !isIfInstruction(item) || item.Target <= int32(item.Offset) {
			continue
		}
		targetIndex, ok := offsetIndex[uint32(item.Target)]
		if !ok || targetIndex <= i+1 || targetIndex >= len(ins)-1 {
			continue
		}
		gotoIndex := -1
		for j := i + 1; j < targetIndex; j++ {
			if isGotoInstruction(ins[j]) && ins[j].Target > int32(ins[j].Offset) {
				gotoIndex = j
				break
			}
		}
		if gotoIndex < 0 {
			continue
		}
		joinIndex, ok := offsetIndex[uint32(ins[gotoIndex].Target)]
		if !ok || joinIndex <= targetIndex || joinIndex >= len(ins) || ins[joinIndex].Name != "return-void" {
			continue
		}
		values, prefixStatements, ok := evaluateSimplePrefix(method, ins[:i], opts)
		if !ok {
			continue
		}
		cond := conditionExpression(method, item, opts, values)
		if cond == "" {
			continue
		}
		fallValues := cloneValues(values)
		fallBlock, ok := evaluateLinearInstructions(method, ins[i+1:gotoIndex], opts, fallValues, false)
		if !ok || fallBlock.returns {
			continue
		}
		targetValues := cloneValues(values)
		targetBlock, ok := evaluateLinearInstructions(method, ins[targetIndex:joinIndex], opts, targetValues, false)
		if !ok || targetBlock.returns {
			continue
		}
		b.WriteString("        // jadx-go: decompiled simple void if/goto body.\n")
		for _, stmt := range prefixStatements {
			fmt.Fprintf(b, "        %s\n", stmt)
		}
		fmt.Fprintf(b, "        if (%s) {\n", cond)
		for _, stmt := range targetBlock.statements {
			fmt.Fprintf(b, "            %s\n", stmt)
		}
		b.WriteString("            return;\n")
		b.WriteString("        }\n")
		for _, stmt := range fallBlock.statements {
			fmt.Fprintf(b, "        %s\n", stmt)
		}
		return true
	}
	return false
}

func tryWriteGuardedReturnBody(b *strings.Builder, method EncodedMethod, ins []Instruction, opts JavaWriteOptions) bool {
	ret := javaType(method.ID.ReturnType, opts)
	if ret == "void" || method.ID.Name == "<init>" || method.ID.Name == "<clinit>" || len(ins) < 4 {
		return false
	}
	offsetIndex := instructionOffsetIndex(ins)
	for _, item := range ins {
		if !isIfInstruction(item) || item.Target <= int32(item.Offset) {
			continue
		}
		targetIndex, ok := offsetIndex[uint32(item.Target)]
		if !ok || targetIndex <= 0 || targetIndex >= len(ins) {
			continue
		}
		defaultBlock, ok := evaluateSimpleReturnBlock(method, ins[targetIndex:], opts, map[int]string{})
		if !ok || !defaultBlock.returns || len(defaultBlock.statements) != 0 {
			continue
		}
		lines, ok := buildGuardedReturnLines(method, ins[:targetIndex], opts, returnExpressionForType(defaultBlock.expr, ret), func(target int32) bool {
			return target == item.Target
		})
		if !ok {
			continue
		}
		b.WriteString("        // jadx-go: decompiled simple guarded return body.\n")
		for _, line := range lines {
			fmt.Fprintf(b, "        %s\n", line)
		}
		return true
	}
	return false
}

func tryWriteSimpleTryCatchReturnBody(b *strings.Builder, method EncodedMethod, ins []Instruction, opts JavaWriteOptions) bool {
	ret := javaType(method.ID.ReturnType, opts)
	if method.Code.Tries == 0 || ret == "void" || method.ID.Name == "<init>" || method.ID.Name == "<clinit>" {
		return false
	}
	catchIndex := -1
	for i, item := range ins {
		if item.Name == "move-exception" {
			catchIndex = i
			break
		}
	}
	if catchIndex <= 0 || catchIndex >= len(ins)-1 || len(ins[catchIndex].Registers) == 0 {
		return false
	}
	catchValues := map[int]string{ins[catchIndex].Registers[0]: "e"}
	catchBlock, ok := evaluateLinearInstructions(method, ins[catchIndex+1:], opts, catchValues, true)
	if !ok || !catchBlock.returns {
		return false
	}
	catchExpr := returnExpressionForType(catchBlock.expr, ret)
	tryLines, ok := buildGuardedReturnLines(method, ins[:catchIndex], opts, catchExpr, func(target int32) bool {
		return target >= int32(ins[catchIndex].Offset)
	})
	if !ok {
		return false
	}
	b.WriteString("        // jadx-go: decompiled simple try/catch guarded return body.\n")
	b.WriteString("        try {\n")
	for _, line := range tryLines {
		fmt.Fprintf(b, "            %s\n", line)
	}
	b.WriteString("        } catch (Exception e) {\n")
	for _, stmt := range catchBlock.statements {
		fmt.Fprintf(b, "            %s\n", stmt)
	}
	fmt.Fprintf(b, "            return %s;\n", catchExpr)
	b.WriteString("        }\n")
	return true
}

func buildGuardedReturnLines(method EncodedMethod, ins []Instruction, opts JavaWriteOptions, defaultExpr string, targetAllowed func(int32) bool) ([]string, bool) {
	if len(ins) == 0 || targetAllowed == nil {
		return nil, false
	}
	values := map[int]string{}
	lines := make([]string, 0, len(ins))
	guards := 0
	cursor := 0
	for cursor < len(ins) {
		guardIndex := -1
		for i := cursor; i < len(ins); i++ {
			if isIfInstruction(ins[i]) && targetAllowed(ins[i].Target) {
				guardIndex = i
				break
			}
		}
		if guardIndex < 0 {
			break
		}
		prefix, ok := evaluateLinearInstructions(method, ins[cursor:guardIndex], opts, values, false)
		if !ok || prefix.returns {
			return nil, false
		}
		lines = append(lines, prefix.statements...)
		cond := conditionExpression(method, ins[guardIndex], opts, values)
		if cond == "" {
			return nil, false
		}
		lines = append(lines, fmt.Sprintf("if (%s) {", cond))
		lines = append(lines, "    return "+defaultExpr+";")
		lines = append(lines, "}")
		guards++
		cursor = guardIndex + 1
	}
	if guards == 0 || cursor >= len(ins) {
		return nil, false
	}
	body, ok := evaluateLinearInstructions(method, ins[cursor:], opts, values, true)
	if !ok || !body.returns {
		return nil, false
	}
	lines = append(lines, body.statements...)
	if body.expr != "" {
		lines = append(lines, "return "+returnExpressionForType(body.expr, javaType(method.ID.ReturnType, opts))+";")
	}
	return lines, true
}

func tryWriteSynchronizedBody(b *strings.Builder, method EncodedMethod, ins []Instruction, opts JavaWriteOptions) bool {
	if len(ins) < 4 || method.ID.Name == "<init>" || method.ID.Name == "<clinit>" {
		return false
	}
	ret := javaType(method.ID.ReturnType, opts)
	for enterIndex, item := range ins {
		if item.Name != "monitor-enter" || len(item.Registers) == 0 {
			continue
		}
		lockReg := item.Registers[0]
		exitIndex := -1
		for j := enterIndex + 1; j < len(ins); j++ {
			if ins[j].Name == "monitor-exit" && len(ins[j].Registers) > 0 && ins[j].Registers[0] == lockReg {
				exitIndex = j
				break
			}
		}
		if exitIndex < 0 || exitIndex+1 >= len(ins) {
			continue
		}
		values, prefixStatements, ok := evaluateSimplePrefix(method, ins[:enterIndex], opts)
		if !ok {
			continue
		}
		lockExpr := valueExpression(method, values, lockReg)
		if lockExpr == "" {
			continue
		}
		bodyValues := cloneValues(values)
		bodyBlock, ok := evaluateLinearInstructions(method, ins[enterIndex+1:exitIndex], opts, bodyValues, false)
		if !ok || bodyBlock.returns {
			continue
		}
		tailBlock, ok := evaluateLinearInstructions(method, ins[exitIndex+1:], opts, bodyValues, true)
		if !ok || !tailBlock.returns {
			continue
		}
		b.WriteString("        // jadx-go: decompiled simple synchronized block body.\n")
		for _, stmt := range prefixStatements {
			fmt.Fprintf(b, "        %s\n", stmt)
		}
		fmt.Fprintf(b, "        synchronized (%s) {\n", lockExpr)
		for _, stmt := range bodyBlock.statements {
			fmt.Fprintf(b, "            %s\n", stmt)
		}
		b.WriteString("        }\n")
		for _, stmt := range tailBlock.statements {
			fmt.Fprintf(b, "        %s\n", stmt)
		}
		if tailBlock.expr != "" && ret != "void" {
			fmt.Fprintf(b, "        return %s;\n", returnExpressionForType(tailBlock.expr, ret))
		}
		return true
	}
	return false
}

func tryWriteLinearJavaBody(b *strings.Builder, method EncodedMethod, ins []Instruction, opts JavaWriteOptions) bool {
	if len(ins) == 0 {
		return false
	}
	values := map[int]string{}
	newTypes := map[int]string{}
	localNames := map[string]int{}
	statements := make([]string, 0, len(ins))
	pendingCall := ""
	ret := javaType(method.ID.ReturnType, opts)
	for idx, item := range ins {
		last := idx == len(ins)-1
		if !flushIgnoredPendingCall(&pendingCall, &statements, item) {
			return false
		}
		switch {
		case isConstInstruction(item):
			if pendingCall != "" || len(item.Registers) == 0 {
				return false
			}
			values[item.Registers[0]] = constExpression(item)
		case isMoveInstruction(item):
			if pendingCall != "" || len(item.Registers) < 2 {
				return false
			}
			values[item.Registers[0]] = rawValueExpression(method, values, item.Registers[1])
		case isMoveResultInstruction(item):
			if pendingCall == "" || len(item.Registers) == 0 {
				return false
			}
			values[item.Registers[0]] = pendingCall
			pendingCall = ""
		case item.Name == "const-class":
			if pendingCall != "" || len(item.Registers) == 0 || item.TypeRef == "" {
				return false
			}
			values[item.Registers[0]] = javaType(item.TypeRef, opts) + ".class"
		case item.Name == "check-cast":
			if pendingCall != "" || len(item.Registers) == 0 || item.TypeRef == "" {
				return false
			}
			reg := item.Registers[0]
			values[reg] = typedExpression("(("+javaType(item.TypeRef, opts)+") "+valueExpression(method, values, reg)+")", item.TypeRef)
		case item.Name == "array-length":
			if pendingCall != "" || len(item.Registers) < 2 {
				return false
			}
			values[item.Registers[0]] = typedExpression(valueExpression(method, values, item.Registers[1])+".length", "I")
		case item.Name == "new-array":
			if pendingCall != "" || len(item.Registers) < 2 || item.TypeRef == "" {
				return false
			}
			arrayName := localArrayName(item.Registers[0], localNames)
			statements = append(statements, javaType(item.TypeRef, opts)+" "+arrayName+" = new "+javaArrayComponentType(item.TypeRef, opts)+"["+valueExpression(method, values, item.Registers[1])+"];")
			values[item.Registers[0]] = typedExpression(arrayName, item.TypeRef)
		case item.Name == "fill-array-data":
			if pendingCall != "" || len(item.Registers) == 0 || len(item.ArrayDataValues) == 0 {
				return false
			}
			reg := item.Registers[0]
			desc := expressionDescriptor(values[reg])
			if desc == "" {
				desc = "[I"
			}
			values[reg] = typedExpression(arrayDataLiteral(desc, item.ArrayDataElementWidth, item.ArrayDataValues, opts), desc)
		case strings.HasPrefix(item.Name, "filled-new-array"):
			if pendingCall != "" || item.TypeRef == "" {
				return false
			}
			args := make([]string, 0, len(item.Registers))
			for _, reg := range item.Registers {
				args = append(args, valueExpression(method, values, reg))
			}
			pendingCall = "new " + javaArrayComponentType(item.TypeRef, opts) + "[]{" + strings.Join(args, ", ") + "}"
		case item.Name == "instance-of":
			if pendingCall != "" || len(item.Registers) < 2 || item.TypeRef == "" {
				return false
			}
			values[item.Registers[0]] = typedExpression("("+valueExpression(method, values, item.Registers[1])+" instanceof "+javaType(item.TypeRef, opts)+")", "Z")
		case isArrayGet(item):
			if pendingCall != "" || len(item.Registers) < 3 {
				return false
			}
			arrayExpr := valueExpression(method, values, item.Registers[1])
			idxExpr := valueExpression(method, values, item.Registers[2])
			expr := arrayExpr + "[" + idxExpr + "]"
			if component := arrayComponentDescriptor(expressionDescriptor(values[item.Registers[1]])); component != "" {
				expr = typedExpression(expr, component)
			}
			values[item.Registers[0]] = expr
		case isArrayPut(item):
			if pendingCall != "" || len(item.Registers) < 3 {
				return false
			}
			arrayExpr := valueExpression(method, values, item.Registers[1])
			valueExpr := valueExpression(method, values, item.Registers[0])
			if component := arrayComponentDescriptor(expressionDescriptor(values[item.Registers[1]])); component != "" {
				valueExpr = expressionForDescriptor(valueExpr, component)
			}
			statements = append(statements, arrayExpr+"["+valueExpression(method, values, item.Registers[2])+"] = "+valueExpr+";")
		case item.Name == "new-instance":
			if pendingCall != "" || len(item.Registers) == 0 || item.TypeRef == "" {
				return false
			}
			newTypes[item.Registers[0]] = item.TypeRef
			values[item.Registers[0]] = typedExpression("new "+javaType(item.TypeRef, opts), item.TypeRef)
		case isFieldGet(item):
			if pendingCall != "" || len(item.Registers) == 0 {
				return false
			}
			expr := fieldReadExpressionWithValues(method, item, opts, values)
			if expr == "" {
				return false
			}
			values[item.Registers[0]] = typedExpression(expr, item.FieldRef.Type)
		case isFieldPut(item):
			if pendingCall != "" {
				return false
			}
			stmt := fieldWriteStatementWithValues(method, item, opts, values)
			if stmt == "" {
				return false
			}
			statements = append(statements, stmt+";")
		case isUnaryInstruction(item):
			if pendingCall != "" || len(item.Registers) < 2 {
				return false
			}
			expr := unaryExpression(method, item, opts, values)
			if expr == "" {
				return false
			}
			values[item.Registers[0]] = expr
		case isCompareInstruction(item):
			if pendingCall != "" || len(item.Registers) < 3 {
				return false
			}
			expr := compareExpression(method, item, values)
			if expr == "" {
				return false
			}
			values[item.Registers[0]] = typedExpression(expr, "I")
		case isBinaryInstruction(item):
			if pendingCall != "" {
				return false
			}
			expr := binaryExpression(method, item, values)
			if expr == "" || len(item.Registers) == 0 {
				return false
			}
			if desc := binaryResultDescriptor(item); desc != "" {
				expr = typedExpression(expr, desc)
			}
			values[item.Registers[0]] = expr
		case isInvokeInstruction(item):
			if pendingCall != "" {
				return false
			}
			if item.MethodRef.Name == "<init>" && strings.HasPrefix(item.Name, "invoke-direct") && len(item.Registers) > 0 {
				recv := item.Registers[0]
				if typ, ok := newTypes[recv]; ok {
					args := argumentExpressions(method, item.Registers[1:], item.MethodRef.Parameters, values)
					values[recv] = typedExpression("new "+javaType(typ, opts)+"("+strings.Join(args, ", ")+")", typ)
					delete(newTypes, recv)
					continue
				}
			}
			call := methodCallExpression(method, item, opts, values)
			if call == "" {
				return false
			}
			if javaType(item.MethodRef.ReturnType, opts) == "void" {
				statements = append(statements, call+";")
			} else if idx+1 >= len(ins) || !isMoveResultInstruction(ins[idx+1]) {
				if recv, ok := fluentReceiverRegister(item); ok {
					values[recv] = typedExpression(call, item.MethodRef.ReturnType)
				} else {
					statements = append(statements, call+";")
				}
			} else {
				pendingCall = typedExpression(call, item.MethodRef.ReturnType)
			}
		case item.Name == "throw":
			if pendingCall != "" || !last || len(item.Registers) == 0 {
				return false
			}
			b.WriteString("        // jadx-go: decompiled simple throw method body.\n")
			for _, stmt := range statements {
				fmt.Fprintf(b, "        %s\n", stmt)
			}
			fmt.Fprintf(b, "        throw %s;\n", throwExpression(method, values, item.Registers[0]))
			return true
		case item.Name == "return-void":
			if pendingCall != "" || !last {
				return false
			}
			if len(statements) == 0 {
				b.WriteString("        // jadx-go: decompiled simple empty method.\n")
				return true
			}
			b.WriteString("        // jadx-go: decompiled simple linear method body.\n")
			for _, stmt := range statements {
				fmt.Fprintf(b, "        %s\n", stmt)
			}
			return true
		case isReturnWithRegister(item):
			if pendingCall != "" || !last || len(item.Registers) == 0 {
				return false
			}
			expr := valueExpression(method, values, item.Registers[0])
			expr = returnExpressionForType(expr, ret)
			b.WriteString("        // jadx-go: decompiled simple linear return.\n")
			for _, stmt := range statements {
				fmt.Fprintf(b, "        %s\n", stmt)
			}
			fmt.Fprintf(b, "        return %s;\n", expr)
			return true
		default:
			return false
		}
	}
	return false
}

type simpleBlockResult struct {
	statements []string
	expr       string
	returns    bool
}

func isMoveResultInstruction(in Instruction) bool {
	return in.Name == "move-result" || in.Name == "move-result-wide" || in.Name == "move-result-object"
}

func flushIgnoredPendingCall(pending *string, statements *[]string, next Instruction) bool {
	if pending == nil || *pending == "" {
		return true
	}
	if isMoveResultInstruction(next) {
		return true
	}
	if statements == nil {
		return false
	}
	*statements = append(*statements, expressionText(*pending)+";")
	*pending = ""
	return true
}

func tryWriteSimpleIfReturnBody(b *strings.Builder, method EncodedMethod, ins []Instruction, opts JavaWriteOptions) bool {
	ret := javaType(method.ID.ReturnType, opts)
	if ret == "void" || method.ID.Name == "<init>" || method.ID.Name == "<clinit>" {
		return false
	}
	offsetIndex := instructionOffsetIndex(ins)
	for i, item := range ins {
		if !isIfInstruction(item) || item.Target <= int32(item.Offset) {
			continue
		}
		targetIndex, ok := offsetIndex[uint32(item.Target)]
		if !ok || targetIndex <= i+1 || targetIndex >= len(ins) {
			continue
		}
		values, prefixStatements, ok := evaluateSimplePrefix(method, ins[:i], opts)
		if !ok {
			continue
		}
		cond := conditionExpression(method, item, opts, values)
		if cond == "" {
			continue
		}
		fall, ok := evaluateSimpleReturnBlock(method, ins[i+1:targetIndex], opts, cloneValues(values))
		if !ok || !fall.returns {
			continue
		}
		target, ok := evaluateSimpleReturnBlock(method, ins[targetIndex:], opts, cloneValues(values))
		if !ok || !target.returns {
			continue
		}
		b.WriteString("        // jadx-go: decompiled simple conditional return.\n")
		for _, stmt := range prefixStatements {
			fmt.Fprintf(b, "        %s\n", stmt)
		}
		fmt.Fprintf(b, "        if (%s) {\n", cond)
		for _, stmt := range target.statements {
			fmt.Fprintf(b, "            %s\n", stmt)
		}
		fmt.Fprintf(b, "            return %s;\n", returnExpressionForType(target.expr, ret))
		b.WriteString("        }\n")
		for _, stmt := range fall.statements {
			fmt.Fprintf(b, "        %s\n", stmt)
		}
		fmt.Fprintf(b, "        return %s;\n", returnExpressionForType(fall.expr, ret))
		return true
	}
	return false
}

func evaluateSimplePrefix(method EncodedMethod, ins []Instruction, opts JavaWriteOptions) (map[int]string, []string, bool) {
	values := map[int]string{}
	block, ok := evaluateLinearInstructions(method, ins, opts, values, false)
	if !ok || block.returns {
		return nil, nil, false
	}
	return values, block.statements, true
}

func evaluateSimpleReturnBlock(method EncodedMethod, ins []Instruction, opts JavaWriteOptions, values map[int]string) (simpleBlockResult, bool) {
	return evaluateLinearInstructions(method, ins, opts, values, true)
}

func evaluateLinearInstructions(method EncodedMethod, ins []Instruction, opts JavaWriteOptions, values map[int]string, requireReturn bool) (simpleBlockResult, bool) {
	if values == nil {
		values = map[int]string{}
	}
	newTypes := map[int]string{}
	localNames := map[string]int{}
	var out simpleBlockResult
	pendingCall := ""
	for idx, item := range ins {
		last := idx == len(ins)-1
		if !flushIgnoredPendingCall(&pendingCall, &out.statements, item) {
			return out, false
		}
		switch {
		case isConstInstruction(item):
			if pendingCall != "" || len(item.Registers) == 0 {
				return out, false
			}
			values[item.Registers[0]] = constExpression(item)
		case isMoveInstruction(item):
			if pendingCall != "" || len(item.Registers) < 2 {
				return out, false
			}
			values[item.Registers[0]] = rawValueExpression(method, values, item.Registers[1])
		case isMoveResultInstruction(item):
			if pendingCall == "" || len(item.Registers) == 0 {
				return out, false
			}
			values[item.Registers[0]] = pendingCall
			pendingCall = ""
		case item.Name == "const-class":
			if pendingCall != "" || len(item.Registers) == 0 || item.TypeRef == "" {
				return out, false
			}
			values[item.Registers[0]] = javaType(item.TypeRef, opts) + ".class"
		case item.Name == "check-cast":
			if pendingCall != "" || len(item.Registers) == 0 || item.TypeRef == "" {
				return out, false
			}
			reg := item.Registers[0]
			values[reg] = typedExpression("(("+javaType(item.TypeRef, opts)+") "+valueExpression(method, values, reg)+")", item.TypeRef)
		case item.Name == "array-length":
			if pendingCall != "" || len(item.Registers) < 2 {
				return out, false
			}
			values[item.Registers[0]] = typedExpression(valueExpression(method, values, item.Registers[1])+".length", "I")
		case item.Name == "new-array":
			if pendingCall != "" || len(item.Registers) < 2 || item.TypeRef == "" {
				return out, false
			}
			arrayName := localArrayName(item.Registers[0], localNames)
			out.statements = append(out.statements, javaType(item.TypeRef, opts)+" "+arrayName+" = new "+javaArrayComponentType(item.TypeRef, opts)+"["+valueExpression(method, values, item.Registers[1])+"];")
			values[item.Registers[0]] = typedExpression(arrayName, item.TypeRef)
		case item.Name == "fill-array-data":
			if pendingCall != "" || len(item.Registers) == 0 || len(item.ArrayDataValues) == 0 {
				return out, false
			}
			reg := item.Registers[0]
			desc := expressionDescriptor(values[reg])
			if desc == "" {
				desc = "[I"
			}
			values[reg] = typedExpression(arrayDataLiteral(desc, item.ArrayDataElementWidth, item.ArrayDataValues, opts), desc)
		case strings.HasPrefix(item.Name, "filled-new-array"):
			if pendingCall != "" || item.TypeRef == "" {
				return out, false
			}
			args := make([]string, 0, len(item.Registers))
			for _, reg := range item.Registers {
				args = append(args, valueExpression(method, values, reg))
			}
			pendingCall = "new " + javaArrayComponentType(item.TypeRef, opts) + "[]{" + strings.Join(args, ", ") + "}"
		case item.Name == "instance-of":
			if pendingCall != "" || len(item.Registers) < 2 || item.TypeRef == "" {
				return out, false
			}
			values[item.Registers[0]] = typedExpression("("+valueExpression(method, values, item.Registers[1])+" instanceof "+javaType(item.TypeRef, opts)+")", "Z")
		case item.Name == "new-instance":
			if pendingCall != "" || len(item.Registers) == 0 || item.TypeRef == "" {
				return out, false
			}
			newTypes[item.Registers[0]] = item.TypeRef
			values[item.Registers[0]] = typedExpression("new "+javaType(item.TypeRef, opts), item.TypeRef)
		case isArrayGet(item):
			if pendingCall != "" || len(item.Registers) < 3 {
				return out, false
			}
			arrayExpr := valueExpression(method, values, item.Registers[1])
			idxExpr := valueExpression(method, values, item.Registers[2])
			expr := arrayExpr + "[" + idxExpr + "]"
			if component := arrayComponentDescriptor(expressionDescriptor(values[item.Registers[1]])); component != "" {
				expr = typedExpression(expr, component)
			}
			values[item.Registers[0]] = expr
		case isArrayPut(item):
			if pendingCall != "" || len(item.Registers) < 3 {
				return out, false
			}
			arrayExpr := valueExpression(method, values, item.Registers[1])
			valueExpr := valueExpression(method, values, item.Registers[0])
			if component := arrayComponentDescriptor(expressionDescriptor(values[item.Registers[1]])); component != "" {
				valueExpr = expressionForDescriptor(valueExpr, component)
			}
			out.statements = append(out.statements, arrayExpr+"["+valueExpression(method, values, item.Registers[2])+"] = "+valueExpr+";")
		case isFieldGet(item):
			if pendingCall != "" || len(item.Registers) == 0 {
				return out, false
			}
			expr := fieldReadExpressionWithValues(method, item, opts, values)
			if expr == "" {
				return out, false
			}
			values[item.Registers[0]] = typedExpression(expr, item.FieldRef.Type)
		case isFieldPut(item):
			if pendingCall != "" {
				return out, false
			}
			stmt := fieldWriteStatementWithValues(method, item, opts, values)
			if stmt == "" {
				return out, false
			}
			out.statements = append(out.statements, stmt+";")
		case isUnaryInstruction(item):
			if pendingCall != "" || len(item.Registers) < 2 {
				return out, false
			}
			expr := unaryExpression(method, item, opts, values)
			if expr == "" {
				return out, false
			}
			values[item.Registers[0]] = expr
		case isCompareInstruction(item):
			if pendingCall != "" || len(item.Registers) < 3 {
				return out, false
			}
			expr := compareExpression(method, item, values)
			if expr == "" {
				return out, false
			}
			values[item.Registers[0]] = typedExpression(expr, "I")
		case isBinaryInstruction(item):
			if pendingCall != "" {
				return out, false
			}
			expr := binaryExpression(method, item, values)
			if expr == "" || len(item.Registers) == 0 {
				return out, false
			}
			if desc := binaryResultDescriptor(item); desc != "" {
				expr = typedExpression(expr, desc)
			}
			values[item.Registers[0]] = expr
		case isInvokeInstruction(item):
			if pendingCall != "" {
				return out, false
			}
			if item.MethodRef.Name == "<init>" && strings.HasPrefix(item.Name, "invoke-direct") && len(item.Registers) > 0 {
				recv := item.Registers[0]
				if typ, ok := newTypes[recv]; ok {
					args := argumentExpressions(method, item.Registers[1:], item.MethodRef.Parameters, values)
					values[recv] = typedExpression("new "+javaType(typ, opts)+"("+strings.Join(args, ", ")+")", typ)
					delete(newTypes, recv)
					continue
				}
			}
			call := methodCallExpression(method, item, opts, values)
			if call == "" {
				return out, false
			}
			if javaType(item.MethodRef.ReturnType, opts) == "void" {
				out.statements = append(out.statements, call+";")
			} else if idx+1 >= len(ins) || !isMoveResultInstruction(ins[idx+1]) {
				if recv, ok := fluentReceiverRegister(item); ok {
					values[recv] = typedExpression(call, item.MethodRef.ReturnType)
				} else {
					out.statements = append(out.statements, call+";")
				}
			} else {
				pendingCall = typedExpression(call, item.MethodRef.ReturnType)
			}
		case item.Name == "throw":
			if pendingCall != "" || len(item.Registers) == 0 || !last {
				return out, false
			}
			out.statements = append(out.statements, "throw "+throwExpression(method, values, item.Registers[0])+";")
			out.returns = true
			return out, true
		case isReturnWithRegister(item):
			if pendingCall != "" || len(item.Registers) == 0 || !last {
				return out, false
			}
			out.expr = valueExpression(method, values, item.Registers[0])
			out.returns = true
			return out, true
		case item.Name == "return-void":
			if pendingCall != "" || !last {
				return out, false
			}
			out.returns = true
			return out, true
		default:
			return out, false
		}
	}
	if pendingCall != "" {
		return out, false
	}
	if requireReturn && !out.returns {
		return out, false
	}
	return out, true
}

func instructionOffsetIndex(ins []Instruction) map[uint32]int {
	out := make(map[uint32]int, len(ins))
	for i, item := range ins {
		out[item.Offset] = i
	}
	return out
}

func cloneValues(in map[int]string) map[int]string {
	out := map[int]string{}
	for k, v := range in {
		out[k] = v
	}
	return out
}

func isIfInstruction(in Instruction) bool {
	return strings.HasPrefix(in.Name, "if-")
}

func conditionExpression(method EncodedMethod, in Instruction, opts JavaWriteOptions, values map[int]string) string {
	if len(in.Registers) == 0 {
		return ""
	}
	_ = opts
	name := in.Name
	if len(in.Registers) == 1 {
		reg := in.Registers[0]
		a := valueExpression(method, values, reg)
		zero := zeroComparisonLiteral(method, values, reg)
		switch name {
		case "if-eqz":
			if zero == "false" {
				return "!" + parenthesizeBooleanExpr(a)
			}
			return a + " == " + zero
		case "if-nez":
			if zero == "false" {
				return parenthesizeBooleanExpr(a)
			}
			return a + " != " + zero
		case "if-ltz":
			return a + " < 0"
		case "if-gez":
			return a + " >= 0"
		case "if-gtz":
			return a + " > 0"
		case "if-lez":
			return a + " <= 0"
		}
	}
	if len(in.Registers) >= 2 {
		leftReg := in.Registers[0]
		rightReg := in.Registers[1]
		a := valueExpression(method, values, leftReg)
		c := valueExpression(method, values, rightReg)
		leftZero := isZeroLiteralExpression(a)
		rightZero := isZeroLiteralExpression(c)
		if leftZero {
			a = zeroComparisonLiteral(method, values, rightReg)
		}
		if rightZero {
			c = zeroComparisonLiteral(method, values, leftReg)
		}
		if name == "if-eq" || name == "if-ne" {
			if rightZero && c == "false" {
				if name == "if-eq" {
					return "!" + parenthesizeBooleanExpr(a)
				}
				return parenthesizeBooleanExpr(a)
			}
			if leftZero && a == "false" {
				if name == "if-eq" {
					return "!" + parenthesizeBooleanExpr(c)
				}
				return parenthesizeBooleanExpr(c)
			}
		}
		switch name {
		case "if-eq":
			return a + " == " + c
		case "if-ne":
			return a + " != " + c
		case "if-lt":
			return a + " < " + c
		case "if-ge":
			return a + " >= " + c
		case "if-gt":
			return a + " > " + c
		case "if-le":
			return a + " <= " + c
		}
	}
	return ""
}

func isZeroLiteralExpression(expr string) bool {
	text := strings.TrimSpace(expressionText(expr))
	return text == "0" || text == "0L"
}

func zeroComparisonLiteral(method EncodedMethod, values map[int]string, reg int) string {
	expr := valueExpression(method, values, reg)
	if values != nil {
		if raw, ok := values[reg]; ok {
			desc := expressionDescriptor(raw)
			if desc == "Z" {
				return "false"
			}
			if strings.HasPrefix(desc, "L") || strings.HasPrefix(desc, "[") {
				return "null"
			}
			if desc != "" {
				return "0"
			}
			// Once a register has a concrete expression, that expression is more
			// trustworthy than the register's original parameter descriptor. Dalvik
			// commonly reuses p0/this as an int temporary in small bit-mask checks.
			if isKnownPrimitiveZeroCompareExpression(expr) || isLikelyNumericExpression(expr) {
				return "0"
			}
			if expr == "true" || expr == "false" || isLikelyBooleanExpression(expr) {
				return "false"
			}
			if expr == "null" || isLikelyReferenceExpression(expr) {
				return "null"
			}
			return "0"
		}
	}
	if isKnownPrimitiveZeroCompareExpression(expr) || isLikelyNumericExpression(expr) {
		return "0"
	}
	if expr == "true" || expr == "false" || isLikelyBooleanRegister(method, reg) || isLikelyBooleanExpression(expr) {
		return "false"
	}
	if expr == "null" || isLikelyReferenceRegister(method, reg) || isLikelyReferenceExpression(expr) {
		return "null"
	}
	return "0"
}

func isLikelyBooleanRegister(method EncodedMethod, reg int) bool {
	desc := registerDescriptor(method, reg)
	return desc == "Z"
}

func isLikelyReferenceRegister(method EncodedMethod, reg int) bool {
	desc := registerDescriptor(method, reg)
	return strings.HasPrefix(desc, "L") || strings.HasPrefix(desc, "[")
}

func registerDescriptor(method EncodedMethod, reg int) string {
	if !method.Code.HasCode {
		return ""
	}
	paramStart := int(method.Code.Registers) - int(method.Code.Ins)
	if reg < paramStart || paramStart < 0 {
		return ""
	}
	cursor := paramStart
	if method.AccessFlags&0x0008 == 0 {
		if reg == cursor {
			return method.ID.Class
		}
		cursor++
	}
	for _, desc := range method.ID.Parameters {
		width := descriptorRegisterWidth(desc)
		if reg == cursor || (width == 2 && reg == cursor+1) {
			return desc
		}
		cursor += width
	}
	return ""
}

func isLikelyBooleanExpression(expr string) bool {
	text := strings.TrimSpace(expressionText(expr))
	if text == "true" || text == "false" {
		return true
	}
	if strings.Contains(text, " instanceof ") || strings.Contains(text, " == ") || strings.Contains(text, " != ") || strings.Contains(text, " <= ") || strings.Contains(text, " >= ") || strings.Contains(text, " < ") || strings.Contains(text, " > ") {
		return true
	}
	booleanCallMarkers := []string{".is", ".has", ".can", ".should", ".contains", ".startsWith", ".endsWith", ".equals", ".matches"}
	if strings.HasSuffix(text, ")") {
		for _, marker := range booleanCallMarkers {
			if strings.Contains(text, marker) {
				return true
			}
		}
	}
	return false
}

func isKnownPrimitiveZeroCompareExpression(expr string) bool {
	text := strings.TrimSpace(expressionText(expr))
	if text == "" {
		return false
	}
	if strings.HasSuffix(text, ".length") {
		return true
	}
	primitiveCalls := []string{
		".length()", ".size()", ".getSize()", ".hashCode()", ".getVisibility()",
		".indexOf(", ".lastIndexOf(", ".ordinal()", ".intValue()", ".longValue()",
		".shortValue()", ".byteValue()", ".charValue()", ".floatValue()", ".doubleValue()",
	}
	for _, marker := range primitiveCalls {
		if strings.Contains(text, marker) {
			return true
		}
	}
	return false
}

func isLikelyNumericExpression(expr string) bool {
	text := strings.TrimSpace(expressionText(expr))
	if text == "" {
		return false
	}
	if _, ok := parseIntegerLiteral(text); ok {
		return true
	}
	if strings.HasPrefix(text, "Long.compare(") || strings.HasPrefix(text, "Float.compare(") || strings.HasPrefix(text, "Double.compare(") {
		return true
	}
	stripped := stripBalancedOuterParens(text)
	if stripped != text {
		return isLikelyNumericExpression(stripped)
	}
	numericOps := []string{" & ", " | ", " ^ ", " << ", " >> ", " >>> ", " + ", " - ", " * ", " / ", " % "}
	for _, op := range numericOps {
		if strings.Contains(text, op) {
			return true
		}
	}
	return false
}

func isLikelyReferenceExpression(expr string) bool {
	if expr == "null" || strings.HasPrefix(expr, "new ") || strings.HasPrefix(expr, "\"") || strings.HasSuffix(expr, ".class") {
		return true
	}
	// Unknown method-call results must not be guessed as references: common Android
	// APIs like View.getVisibility(), Collection.size(), and Float.compare() return
	// primitives and should compare against 0/false, not null. Descriptor-tagged
	// expressions are handled earlier by zeroComparisonLiteral.
	if strings.Contains(expr, "(") || strings.Contains(expr, ")") {
		return false
	}
	// Untyped array indexing may be a primitive or reference element. Prefer a
	// numeric zero comparison unless a descriptor-tagged expression proved it is
	// an object/array reference earlier.
	if strings.Contains(expr, "[") || strings.Contains(expr, "]") {
		return false
	}
	if strings.Contains(expr, ".") && !strings.ContainsAny(expr, "+-*/%&|<>=") {
		return true
	}
	return false
}

func parenthesizeBooleanExpr(expr string) string {
	if strings.HasPrefix(expr, "(") && strings.HasSuffix(expr, ")") {
		return expr
	}
	if strings.ContainsAny(expr, " <>=!&|") {
		return "(" + expr + ")"
	}
	return expr
}

func isConstInstruction(in Instruction) bool {
	switch in.Name {
	case "const/4", "const/16", "const", "const/high16", "const-wide/16", "const-wide/32", "const-wide", "const-wide/high16", "const-string", "const-string/jumbo":
		return true
	default:
		return false
	}
}

func constExpression(in Instruction) string {
	switch in.Name {
	case "const-string", "const-string/jumbo":
		return quoteJava(in.StringLiteral)
	default:
		if in.IntLiteral == nil {
			return "0"
		}
		if strings.HasPrefix(in.Name, "const-wide") {
			return fmt.Sprintf("%dL", *in.IntLiteral)
		}
		return fmt.Sprintf("%d", *in.IntLiteral)
	}
}

func isMoveInstruction(in Instruction) bool {
	switch in.Name {
	case "move", "move/from16", "move/16", "move-wide", "move-wide/from16", "move-wide/16", "move-object", "move-object/from16", "move-object/16":
		return true
	default:
		return false
	}
}

func isInvokeInstruction(in Instruction) bool {
	return strings.HasPrefix(in.Name, "invoke-")
}

func fluentReceiverRegister(in Instruction) (int, bool) {
	if strings.HasPrefix(in.Name, "invoke-static") || len(in.Registers) == 0 {
		return 0, false
	}
	if in.MethodRef.ReturnType == "" || in.MethodRef.ReturnType == "V" {
		return 0, false
	}
	// A common R8/Javac shape ignores StringBuilder.append()/similar fluent
	// returns because the receiver object is mutated in place. If the returned
	// type is the same as the invoked class, keep the receiver expression as a
	// chain instead of emitting repeated fresh receiver statements.
	if in.MethodRef.ReturnType == in.MethodRef.Class {
		return in.Registers[0], true
	}
	return 0, false
}

func isUnaryInstruction(in Instruction) bool {
	switch in.Name {
	case "neg-int", "not-int", "neg-long", "not-long", "neg-float", "neg-double", "int-to-long", "int-to-float", "int-to-double", "long-to-int", "long-to-float", "long-to-double", "float-to-int", "float-to-long", "float-to-double", "double-to-int", "double-to-long", "double-to-float", "int-to-byte", "int-to-char", "int-to-short":
		return true
	default:
		return false
	}
}

func unaryExpression(method EncodedMethod, in Instruction, opts JavaWriteOptions, values map[int]string) string {
	if len(in.Registers) < 2 {
		return ""
	}
	src := valueExpression(method, values, in.Registers[1])
	switch in.Name {
	case "neg-int", "neg-long", "neg-float", "neg-double":
		return "(-" + src + ")"
	case "not-int", "not-long":
		return "(~" + src + ")"
	case "int-to-long":
		return "((long) " + src + ")"
	case "int-to-float", "long-to-float":
		return "((float) " + src + ")"
	case "int-to-double", "long-to-double", "float-to-double":
		return "((double) " + src + ")"
	case "long-to-int", "float-to-int", "double-to-int":
		return "((int) " + src + ")"
	case "float-to-long", "double-to-long":
		return "((long) " + src + ")"
	case "double-to-float":
		return "((float) " + src + ")"
	case "int-to-byte":
		return "((byte) " + src + ")"
	case "int-to-char":
		return "((char) " + src + ")"
	case "int-to-short":
		return "((short) " + src + ")"
	default:
		_ = opts
		return ""
	}
}

func isCompareInstruction(in Instruction) bool {
	switch in.Name {
	case "cmpl-float", "cmpg-float", "cmpl-double", "cmpg-double", "cmp-long":
		return true
	default:
		return false
	}
}

func compareExpression(method EncodedMethod, in Instruction, values map[int]string) string {
	if len(in.Registers) < 3 {
		return ""
	}
	left := valueExpression(method, values, in.Registers[1])
	right := valueExpression(method, values, in.Registers[2])
	switch in.Name {
	case "cmp-long":
		return "Long.compare(" + left + ", " + right + ")"
	case "cmpl-float", "cmpg-float":
		return "Float.compare(" + left + ", " + right + ")"
	case "cmpl-double", "cmpg-double":
		return "Double.compare(" + left + ", " + right + ")"
	default:
		return ""
	}
}

func isGotoInstruction(in Instruction) bool {
	return in.Name == "goto" || in.Name == "goto/16" || in.Name == "goto/32"
}

func isBinaryInstruction(in Instruction) bool {
	return binaryOperator(in.Name) != ""
}

func binaryExpression(method EncodedMethod, in Instruction, values map[int]string) string {
	op := binaryOperator(in.Name)
	if op == "" || len(in.Registers) == 0 {
		return ""
	}
	if strings.Contains(in.Name, "/2addr") {
		if len(in.Registers) < 2 {
			return ""
		}
		left := valueExpression(method, values, in.Registers[0])
		right := valueExpression(method, values, in.Registers[1])
		return "(" + left + " " + op + " " + right + ")"
	}
	if strings.Contains(in.Name, "/lit") || in.Name == "rsub-int" || in.Name == "rsub-int/lit8" {
		if len(in.Registers) < 2 || in.IntLiteral == nil {
			return ""
		}
		left := valueExpression(method, values, in.Registers[1])
		lit := fmt.Sprintf("%d", *in.IntLiteral)
		if strings.HasPrefix(in.Name, "rsub-int") {
			return "(" + lit + " - " + left + ")"
		}
		return "(" + left + " " + op + " " + lit + ")"
	}
	if len(in.Registers) < 3 {
		return ""
	}
	left := valueExpression(method, values, in.Registers[1])
	right := valueExpression(method, values, in.Registers[2])
	return "(" + left + " " + op + " " + right + ")"
}

func binaryResultDescriptor(in Instruction) string {
	base := strings.Split(in.Name, "/")[0]
	switch base {
	case "add-long", "sub-long", "mul-long", "div-long", "rem-long", "and-long", "or-long", "xor-long", "shl-long", "shr-long", "ushr-long":
		return "J"
	case "add-float", "sub-float", "mul-float", "div-float", "rem-float":
		return "F"
	case "add-double", "sub-double", "mul-double", "div-double", "rem-double":
		return "D"
	case "add-int", "sub-int", "mul-int", "div-int", "rem-int", "and-int", "or-int", "xor-int", "shl-int", "shr-int", "ushr-int", "rsub-int":
		return "I"
	default:
		return ""
	}
}

func binaryOperator(name string) string {
	base := strings.Split(name, "/")[0]
	switch base {
	case "add-int", "add-long", "add-float", "add-double":
		return "+"
	case "sub-int", "sub-long", "sub-float", "sub-double", "rsub-int":
		return "-"
	case "mul-int", "mul-long", "mul-float", "mul-double":
		return "*"
	case "div-int", "div-long", "div-float", "div-double":
		return "/"
	case "rem-int", "rem-long", "rem-float", "rem-double":
		return "%"
	case "and-int", "and-long":
		return "&"
	case "or-int", "or-long":
		return "|"
	case "xor-int", "xor-long":
		return "^"
	case "shl-int", "shl-long":
		return "<<"
	case "shr-int", "shr-long":
		return ">>"
	case "ushr-int", "ushr-long":
		return ">>>"
	default:
		return ""
	}
}

func localArrayName(reg int, used map[string]int) string {
	base := fmt.Sprintf("array%d", reg)
	if used == nil {
		return base
	}
	count := used[base]
	used[base] = count + 1
	if count == 0 {
		return base
	}
	return fmt.Sprintf("%s_%d", base, count)
}

func valueExpression(method EncodedMethod, values map[int]string, reg int) string {
	if values != nil {
		if expr, ok := values[reg]; ok && expr != "" {
			return expressionText(expr)
		}
	}
	return registerExpression(method, reg)
}

func rawValueExpression(method EncodedMethod, values map[int]string, reg int) string {
	if values != nil {
		if expr, ok := values[reg]; ok && expr != "" {
			return expr
		}
	}
	return registerExpression(method, reg)
}

func throwExpression(method EncodedMethod, values map[int]string, reg int) string {
	expr := valueExpression(method, values, reg)
	if expr == "0" || expr == "0L" {
		return "null"
	}
	return expr
}

const typedExpressionSeparator = "\u0000"

func typedExpression(expr string, desc string) string {
	if expr == "" || desc == "" {
		return expr
	}
	return expr + typedExpressionSeparator + desc
}

func expressionText(expr string) string {
	if idx := strings.LastIndex(expr, typedExpressionSeparator); idx >= 0 {
		return expr[:idx]
	}
	return expr
}

func expressionDescriptor(expr string) string {
	if idx := strings.LastIndex(expr, typedExpressionSeparator); idx >= 0 && idx+len(typedExpressionSeparator) < len(expr) {
		return expr[idx+len(typedExpressionSeparator):]
	}
	return ""
}

func fieldReadExpressionWithValues(method EncodedMethod, in Instruction, opts JavaWriteOptions, values map[int]string) string {
	field := sanitizeIdentifier(in.FieldRef.Name)
	if strings.HasPrefix(in.Name, "sget") {
		return javaType(in.FieldRef.Class, opts) + "." + field
	}
	if len(in.Registers) < 2 {
		return ""
	}
	receiver := receiverExpressionForOwner(method, values, in.Registers[1], in.FieldRef.Class)
	if receiver == "this" {
		return "this." + field
	}
	return receiver + "." + field
}

func fieldWriteStatementWithValues(method EncodedMethod, in Instruction, opts JavaWriteOptions, values map[int]string) string {
	field := sanitizeIdentifier(in.FieldRef.Name)
	if strings.HasPrefix(in.Name, "sput") {
		if len(in.Registers) < 1 {
			return ""
		}
		value := expressionForDescriptor(valueExpression(method, values, in.Registers[0]), in.FieldRef.Type)
		return javaType(in.FieldRef.Class, opts) + "." + field + " = " + value
	}
	if len(in.Registers) < 2 {
		return ""
	}
	receiver := receiverExpressionForOwner(method, values, in.Registers[1], in.FieldRef.Class)
	prefix := receiver + "."
	if receiver == "this" {
		prefix = "this."
	}
	value := expressionForDescriptor(valueExpression(method, values, in.Registers[0]), in.FieldRef.Type)
	return prefix + field + " = " + value
}

func methodCallExpression(method EncodedMethod, in Instruction, opts JavaWriteOptions, values map[int]string) string {
	if len(in.Registers) == 0 && !strings.HasPrefix(in.Name, "invoke-static") {
		return ""
	}
	name := in.MethodRef.Name
	if name == "" || name == "<init>" || name == "<clinit>" {
		return ""
	}
	argsRegs := in.Registers
	target := ""
	if strings.HasPrefix(in.Name, "invoke-static") {
		target = javaType(in.MethodRef.Class, opts) + "." + sanitizeIdentifier(name)
	} else {
		receiver := receiverExpressionForOwner(method, values, in.Registers[0], in.MethodRef.Class)
		argsRegs = in.Registers[1:]
		if strings.HasPrefix(in.Name, "invoke-super") {
			receiver = "super"
		}
		target = receiver + "." + sanitizeIdentifier(name)
	}
	args := argumentExpressions(method, argsRegs, in.MethodRef.Parameters, values)
	return target + "(" + strings.Join(args, ", ") + ")"
}

func receiverExpressionForOwner(method EncodedMethod, values map[int]string, reg int, ownerDesc string) string {
	base := registerExpression(method, reg)
	if values == nil || ownerDesc == "" {
		return valueExpression(method, values, reg)
	}
	raw, ok := values[reg]
	if !ok || raw == "" {
		return base
	}
	exprDesc := expressionDescriptor(raw)
	if exprDesc == "" || descriptorCompatibleForReceiver(exprDesc, ownerDesc) {
		return expressionText(raw)
	}
	// Dalvik can reuse parameter registers as temporaries. If our carried expression
	// has a descriptor that cannot own this field/method, but the original register
	// descriptor can, prefer the original receiver to avoid stale chains like
	// this.data.data when the bytecode reloads this.data after a prior null flag.
	if descriptorCompatibleForReceiver(registerDescriptor(method, reg), ownerDesc) {
		return base
	}
	return expressionText(raw)
}

func descriptorCompatibleForReceiver(actual string, owner string) bool {
	if actual == "" || owner == "" {
		return false
	}
	if actual == owner || owner == "Ljava/lang/Object;" {
		return true
	}
	if strings.HasPrefix(actual, "[") && owner == "Ljava/lang/Object;" {
		return true
	}
	return false
}

func argumentExpressions(method EncodedMethod, regs []int, params []string, values map[int]string) []string {
	if len(regs) == 0 {
		return nil
	}
	if len(params) == 0 {
		out := make([]string, 0, len(regs))
		for _, reg := range regs {
			out = append(out, valueExpression(method, values, reg))
		}
		return out
	}
	out := make([]string, 0, len(params))
	word := 0
	for _, desc := range params {
		if word >= len(regs) {
			break
		}
		expr := valueExpression(method, values, regs[word])
		out = append(out, expressionForDescriptor(expr, desc))
		word += descriptorRegisterWidth(desc)
	}
	if len(out) == 0 && len(regs) > 0 {
		for _, reg := range regs {
			out = append(out, valueExpression(method, values, reg))
		}
	}
	return out
}

func arrayComponentDescriptor(desc string) string {
	if strings.HasPrefix(desc, "[") && len(desc) > 1 {
		return desc[1:]
	}
	return ""
}

func expressionForDescriptor(expr string, desc string) string {
	expr = expressionText(expr)
	if desc == "Z" {
		if expr == "0" || expr == "0L" {
			return "false"
		}
		if expr == "1" || expr == "1L" {
			return "true"
		}
		if ternary, ok := booleanTernaryExpression(expr); ok {
			return ternary
		}
	}
	if expr == "0" || expr == "0L" {
		if desc == "Z" {
			return "false"
		}
		if desc == "F" {
			return "0.0f"
		}
		if desc == "D" {
			return "0.0d"
		}
		if strings.HasPrefix(desc, "L") || strings.HasPrefix(desc, "[") {
			return "null"
		}
	}
	if (expr == "1" || expr == "1L") && desc == "Z" {
		return "true"
	}
	if desc == "F" {
		if lit, ok := floatLiteralFromIntBits(expr); ok {
			return lit
		}
	}
	if desc == "D" {
		if lit, ok := doubleLiteralFromLongBits(expr); ok {
			return lit
		}
	}
	return expr
}

func booleanTernaryExpression(expr string) (string, bool) {
	text := strings.TrimSpace(stripBalancedOuterParens(expr))
	q := topLevelRuneIndex(text, '?')
	if q < 0 {
		return "", false
	}
	c := topLevelRuneIndex(text[q+1:], ':')
	if c < 0 {
		return "", false
	}
	c += q + 1
	cond := strings.TrimSpace(text[:q])
	left := strings.TrimSpace(text[q+1 : c])
	right := strings.TrimSpace(text[c+1:])
	leftBool, okLeft := booleanBranchExpression(left)
	rightBool, okRight := booleanBranchExpression(right)
	if cond == "" || !okLeft || !okRight {
		return "", false
	}
	return "(" + cond + " ? " + leftBool + " : " + rightBool + ")", true
}

func booleanBranchExpression(expr string) (string, bool) {
	if lit, ok := numericBooleanLiteral(expr); ok {
		return lit, true
	}
	text := strings.TrimSpace(stripBalancedOuterParens(expressionText(expr)))
	if text == "" {
		return "", false
	}
	if isLikelyBooleanExpression(text) {
		return text, true
	}
	if strings.HasPrefix(text, "!") {
		return text, true
	}
	return "", false
}

func numericBooleanLiteral(expr string) (string, bool) {
	switch strings.TrimSpace(stripBalancedOuterParens(expr)) {
	case "0", "0L":
		return "false", true
	case "1", "1L":
		return "true", true
	case "false":
		return "false", true
	case "true":
		return "true", true
	default:
		return "", false
	}
}

func stripBalancedOuterParens(expr string) string {
	text := strings.TrimSpace(expr)
	for strings.HasPrefix(text, "(") && strings.HasSuffix(text, ")") && matchingOuterParens(text) {
		text = strings.TrimSpace(text[1 : len(text)-1])
	}
	return text
}

func matchingOuterParens(text string) bool {
	depth := 0
	for i, r := range text {
		switch r {
		case '(':
			depth++
		case ')':
			depth--
			if depth == 0 && i != len(text)-1 {
				return false
			}
		}
		if depth < 0 {
			return false
		}
	}
	return depth == 0
}

func topLevelRuneIndex(text string, want rune) int {
	depth := 0
	for i, r := range text {
		switch r {
		case '(':
			depth++
		case ')':
			if depth > 0 {
				depth--
			}
		default:
			if r == want && depth == 0 {
				return i
			}
		}
	}
	return -1
}

func returnExpressionForType(expr string, javaRet string) string {
	expr = expressionText(expr)
	if javaRet == "boolean" {
		if expr == "0" || expr == "0L" {
			return "false"
		}
		if expr == "1" || expr == "1L" {
			return "true"
		}
		if ternary, ok := booleanTernaryExpression(expr); ok {
			return ternary
		}
	}
	if javaRet == "float" {
		if expr == "0" || expr == "0L" {
			return "0.0f"
		}
		if lit, ok := floatLiteralFromIntBits(expr); ok {
			return lit
		}
	}
	if javaRet == "double" {
		if expr == "0" || expr == "0L" {
			return "0.0d"
		}
		if lit, ok := doubleLiteralFromLongBits(expr); ok {
			return lit
		}
	}
	if (strings.HasSuffix(javaRet, "[]") || strings.Contains(javaRet, ".")) && (expr == "0" || expr == "0L") {
		return "null"
	}
	return expr
}

func floatLiteralFromIntBits(expr string) (string, bool) {
	raw, ok := parseIntegerLiteral(expr)
	if !ok {
		return "", false
	}
	f := math.Float32frombits(uint32(raw))
	if math.IsNaN(float64(f)) || math.IsInf(float64(f), 0) {
		return fmt.Sprintf("Float.intBitsToFloat(0x%08x)", uint32(raw)), true
	}
	if f == 0 {
		return "0.0f", true
	}
	return strconv.FormatFloat(float64(f), 'g', -1, 32) + "f", true
}

func doubleLiteralFromLongBits(expr string) (string, bool) {
	raw, ok := parseIntegerLiteral(strings.TrimSuffix(expr, "L"))
	if !ok {
		return "", false
	}
	d := math.Float64frombits(uint64(raw))
	if math.IsNaN(d) || math.IsInf(d, 0) {
		return fmt.Sprintf("Double.longBitsToDouble(0x%016xL)", uint64(raw)), true
	}
	if d == 0 {
		return "0.0d", true
	}
	return strconv.FormatFloat(d, 'g', -1, 64) + "d", true
}

func parseIntegerLiteral(expr string) (int64, bool) {
	expr = strings.TrimSpace(expressionText(expr))
	expr = strings.TrimSuffix(expr, "L")
	if expr == "" || strings.ContainsAny(expr, "() +*/%&|^<>,.") || strings.HasPrefix(expr, "\"") {
		return 0, false
	}
	v, err := strconv.ParseInt(expr, 0, 64)
	if err == nil {
		return v, true
	}
	u, err := strconv.ParseUint(expr, 0, 64)
	if err == nil {
		return int64(u), true
	}
	return 0, false
}

func meaningfulInstructions(in []Instruction) []Instruction {
	out := make([]Instruction, 0, len(in))
	for _, item := range in {
		if item.Name == "nop" || isPayloadInstruction(item) {
			continue
		}
		out = append(out, item)
	}
	return out
}

func isReturnWithRegister(in Instruction) bool {
	return (in.Name == "return" || in.Name == "return-wide" || in.Name == "return-object") && len(in.Registers) > 0
}

func isArrayGet(in Instruction) bool {
	switch in.Name {
	case "aget", "aget-wide", "aget-object", "aget-boolean", "aget-byte", "aget-char", "aget-short":
		return true
	default:
		return false
	}
}

func isArrayPut(in Instruction) bool {
	switch in.Name {
	case "aput", "aput-wide", "aput-object", "aput-boolean", "aput-byte", "aput-char", "aput-short":
		return true
	default:
		return false
	}
}

func isFieldGet(in Instruction) bool {
	return strings.HasPrefix(in.Name, "iget") || strings.HasPrefix(in.Name, "sget")
}

func isFieldPut(in Instruction) bool {
	return strings.HasPrefix(in.Name, "iput") || strings.HasPrefix(in.Name, "sput")
}

func sameFirstRegister(a Instruction, b Instruction) bool {
	return len(a.Registers) > 0 && len(b.Registers) > 0 && a.Registers[0] == b.Registers[0]
}

func fieldReadExpression(method EncodedMethod, in Instruction, opts JavaWriteOptions) string {
	field := sanitizeIdentifier(in.FieldRef.Name)
	if strings.HasPrefix(in.Name, "sget") {
		return javaType(in.FieldRef.Class, opts) + "." + field
	}
	if len(in.Registers) < 2 {
		return ""
	}
	receiver := registerExpression(method, in.Registers[1])
	if receiver == "this" {
		return "this." + field
	}
	return receiver + "." + field
}

func fieldWriteStatement(method EncodedMethod, in Instruction, opts JavaWriteOptions) string {
	field := sanitizeIdentifier(in.FieldRef.Name)
	if strings.HasPrefix(in.Name, "sput") {
		if len(in.Registers) < 1 {
			return ""
		}
		value := expressionForDescriptor(registerExpression(method, in.Registers[0]), in.FieldRef.Type)
		return javaType(in.FieldRef.Class, opts) + "." + field + " = " + value
	}
	if len(in.Registers) < 2 {
		return ""
	}
	receiver := registerExpression(method, in.Registers[1])
	prefix := receiver + "."
	if receiver == "this" {
		prefix = "this."
	}
	value := expressionForDescriptor(registerExpression(method, in.Registers[0]), in.FieldRef.Type)
	return prefix + field + " = " + value
}

func registerExpression(method EncodedMethod, reg int) string {
	if !method.Code.HasCode {
		return fmt.Sprintf("v%d", reg)
	}
	paramStart := int(method.Code.Registers) - int(method.Code.Ins)
	if reg < paramStart || paramStart < 0 {
		return fmt.Sprintf("v%d", reg)
	}
	cursor := paramStart
	if method.AccessFlags&0x0008 == 0 {
		if reg == cursor {
			return "this"
		}
		cursor++
	}
	for i, desc := range method.ID.Parameters {
		if reg == cursor {
			return fmt.Sprintf("p%d", i)
		}
		width := descriptorRegisterWidth(desc)
		if width == 2 && reg == cursor+1 {
			return fmt.Sprintf("p%d", i)
		}
		cursor += width
	}
	return fmt.Sprintf("v%d", reg)
}

func descriptorRegisterWidth(desc string) int {
	if desc == "J" || desc == "D" {
		return 2
	}
	return 1
}

func javaLiteralForReturn(v int64, javaRet string) string {
	switch javaRet {
	case "boolean":
		if v == 0 {
			return "false"
		}
		if v == 1 {
			return "true"
		}
	case "char":
		if v >= 0 && v <= 0xffff {
			return fmt.Sprintf("(char) %d", v)
		}
	case "float":
		if v == 0 {
			return "0.0f"
		}
		if lit, ok := floatLiteralFromIntBits(fmt.Sprintf("%d", v)); ok {
			return lit
		}
	case "double":
		if v == 0 {
			return "0.0d"
		}
		if lit, ok := doubleLiteralFromLongBits(fmt.Sprintf("%d", v)); ok {
			return lit
		}
	}
	if strings.HasSuffix(javaRet, "[]") || strings.Contains(javaRet, ".") {
		if v == 0 {
			return "null"
		}
	}
	if javaRet == "long" {
		return fmt.Sprintf("%dL", v)
	}
	return fmt.Sprintf("%d", v)
}

var rawLocalRegisterRef = regexp.MustCompile(`(^|[^.\w$])v[0-9]+\b`)

func recoveredJavaBodyLooksSafe(text string) bool {
	for _, line := range strings.Split(text, "\n") {
		if idx := strings.Index(line, "//"); idx >= 0 {
			line = line[:idx]
		}
		line = stripJavaStringLiterals(line)
		if rawLocalRegisterRef.FindStringIndex(line) != nil {
			return false
		}
		if hasDegenerateTernary(line) {
			return false
		}
	}
	return true
}

func hasDegenerateTernary(line string) bool {
	text := strings.Join(strings.Fields(line), "")
	if text == "" || !strings.Contains(text, "?") || !strings.Contains(text, ":") {
		return false
	}
	literals := []string{"true", "false", "0", "0L", "1", "1L", "null"}
	for _, lit := range literals {
		needle := "?" + lit + ":" + lit
		if strings.Contains(text, needle) {
			return true
		}
	}
	return false
}

func stripJavaStringLiterals(line string) string {
	var b strings.Builder
	inString := false
	inChar := false
	escaped := false
	for _, r := range line {
		if inString {
			if escaped {
				escaped = false
				continue
			}
			if r == '\\' {
				escaped = true
				continue
			}
			if r == '"' {
				inString = false
			}
			continue
		}
		if inChar {
			if escaped {
				escaped = false
				continue
			}
			if r == '\\' {
				escaped = true
				continue
			}
			if r == '\'' {
				inChar = false
			}
			continue
		}
		switch r {
		case '"':
			inString = true
		case '\'':
			inChar = true
		default:
			b.WriteRune(r)
		}
	}
	return b.String()
}

func isPayloadInstruction(in Instruction) bool {
	switch in.Name {
	case "packed-switch-payload", "sparse-switch-payload", "fill-array-data-payload":
		return true
	default:
		return false
	}
}

func arrayDataLiteral(desc string, width uint16, values []int64, opts JavaWriteOptions) string {
	component := javaArrayComponentType(desc, opts)
	parts := make([]string, 0, len(values))
	for _, v := range values {
		parts = append(parts, arrayElementLiteral(desc, width, v))
	}
	return "new " + component + "[]{" + strings.Join(parts, ", ") + "}"
}

func arrayElementLiteral(desc string, width uint16, v int64) string {
	component := desc
	for strings.HasPrefix(component, "[") {
		component = component[1:]
	}
	switch component {
	case "J":
		return fmt.Sprintf("%dL", v)
	case "F":
		return fmt.Sprintf("Float.intBitsToFloat(0x%08x)", uint32(v))
	case "D":
		return fmt.Sprintf("Double.longBitsToDouble(0x%016xL)", uint64(v))
	case "B":
		return fmt.Sprintf("(byte) %d", int8(v))
	case "S":
		return fmt.Sprintf("(short) %d", int16(v))
	case "C":
		return fmt.Sprintf("(char) %d", uint16(v))
	case "Z":
		if v == 0 {
			return "false"
		}
		return "true"
	default:
		_ = width
		return fmt.Sprintf("%d", v)
	}
}

func writeInstructionComments(b *strings.Builder, instructions []Instruction) {
	if len(instructions) == 0 {
		b.WriteString("        // no decoded instructions available.\n")
		return
	}
	b.WriteString("        // Dalvik instructions:\n")
	limit := len(instructions)
	if limit > 120 {
		limit = 120
	}
	for i := 0; i < limit; i++ {
		in := instructions[i]
		fmt.Fprintf(b, "        //   %04x: %s\n", in.Offset, in.Text)
	}
	if len(instructions) > limit {
		fmt.Fprintf(b, "        //   ... %d more instruction(s) omitted from Java output.\n", len(instructions)-limit)
	}
}

func writeFallbackReturn(b *strings.Builder, method EncodedMethod, opts JavaWriteOptions) {
	ret := javaType(method.ID.ReturnType, opts)
	if ret != "void" && method.ID.Name != "<init>" && method.ID.Name != "<clinit>" {
		b.WriteString("        throw new UnsupportedOperationException(\"jadx-go could not decompile this method body yet\");\n")
	}
}

func classModifiers(flags uint32) []string {
	out := visibility(flags)
	if flags&0x0010 != 0 {
		out = append(out, "final")
	}
	if flags&0x0400 != 0 && flags&0x0200 == 0 && flags&0x2000 == 0 {
		out = append(out, "abstract")
	}
	return out
}

func fieldModifiers(flags uint32) []string {
	out := visibility(flags)
	if flags&0x0008 != 0 {
		out = append(out, "static")
	}
	if flags&0x0010 != 0 {
		out = append(out, "final")
	}
	if flags&0x0040 != 0 {
		out = append(out, "volatile")
	}
	if flags&0x0080 != 0 {
		out = append(out, "transient")
	}
	return out
}

func methodModifiers(flags uint32) []string {
	out := visibility(flags)
	if flags&0x0008 != 0 {
		out = append(out, "static")
	}
	if flags&0x0010 != 0 {
		out = append(out, "final")
	}
	if flags&0x0020 != 0 {
		out = append(out, "synchronized")
	}
	if flags&0x0100 != 0 {
		out = append(out, "native")
	}
	if flags&0x0400 != 0 {
		out = append(out, "abstract")
	}
	if flags&0x0800 != 0 {
		out = append(out, "strictfp")
	}
	return out
}

func visibility(flags uint32) []string {
	switch {
	case flags&0x0001 != 0:
		return []string{"public"}
	case flags&0x0002 != 0:
		return []string{"private"}
	case flags&0x0004 != 0:
		return []string{"protected"}
	default:
		return nil
	}
}

func javaArrayComponentType(desc string, opts JavaWriteOptions) string {
	if strings.HasPrefix(desc, "[") {
		return javaType(desc[1:], opts)
	}
	return javaType(desc, opts)
}

func javaTypeList(descs []string, opts JavaWriteOptions) []string {
	out := make([]string, 0, len(descs))
	for _, d := range descs {
		if d != "" {
			out = append(out, javaType(d, opts))
		}
	}
	return out
}

func javaType(desc string, opts JavaWriteOptions) string {
	dims := 0
	for strings.HasPrefix(desc, "[") {
		dims++
		desc = strings.TrimPrefix(desc, "[")
	}
	base := desc
	switch desc {
	case "V":
		base = "void"
	case "Z":
		base = "boolean"
	case "B":
		base = "byte"
	case "S":
		base = "short"
	case "C":
		base = "char"
	case "I":
		base = "int"
	case "J":
		base = "long"
	case "F":
		base = "float"
	case "D":
		base = "double"
	default:
		if strings.HasPrefix(desc, "L") && strings.HasSuffix(desc, ";") {
			base = strings.TrimSuffix(strings.TrimPrefix(desc, "L"), ";")
			base = strings.ReplaceAll(base, "/", ".")
			// Keep binary '$' separators in references until jadx-go supports
			// rendering nested source declarations. Emitting dots while classes are
			// written as top-level Outer$Inner.java files creates invalid references
			// such as Outer..ExternalSyntheticLambda0.
			_ = opts
		} else if desc == "" {
			base = "java.lang.Object"
		}
	}
	for i := 0; i < dims; i++ {
		base += "[]"
	}
	return base
}

func packageName(desc string) string {
	if !strings.HasPrefix(desc, "L") || !strings.HasSuffix(desc, ";") {
		return ""
	}
	body := strings.TrimSuffix(strings.TrimPrefix(desc, "L"), ";")
	slash := strings.LastIndex(body, "/")
	if slash < 0 {
		return ""
	}
	return strings.ReplaceAll(body[:slash], "/", ".")
}

func simpleClassName(desc string) string {
	if strings.HasPrefix(desc, "L") && strings.HasSuffix(desc, ";") {
		body := strings.TrimSuffix(strings.TrimPrefix(desc, "L"), ";")
		slash := strings.LastIndex(body, "/")
		if slash >= 0 {
			body = body[slash+1:]
		}
		if body != "" {
			return body
		}
	}
	return "UnknownClass"
}

func javaFilePath(root string, desc string) string {
	if strings.HasPrefix(desc, "L") && strings.HasSuffix(desc, ";") {
		body := strings.TrimSuffix(strings.TrimPrefix(desc, "L"), ";")
		parts := strings.Split(body, "/")
		for i := range parts {
			parts[i] = sanitizePathSegment(parts[i])
		}
		if len(parts) > 0 && parts[len(parts)-1] == "" {
			parts[len(parts)-1] = "UnknownClass"
		}
		rel := filepath.Join(parts...) + ".java"
		return filepath.Join(root, rel)
	}
	return filepath.Join(root, "UnknownClass.java")
}

func sanitizeIdentifier(name string) string {
	if name == "" {
		return "_"
	}
	var b strings.Builder
	for i, r := range name {
		valid := r == '_' || r == '$' || unicode.IsLetter(r) || (i > 0 && unicode.IsDigit(r))
		if i == 0 && unicode.IsDigit(r) {
			valid = false
		}
		if valid {
			b.WriteRune(r)
		} else {
			b.WriteRune('_')
		}
	}
	out := b.String()
	if out == "" {
		out = "_"
	}
	if javaKeyword(out) {
		out += "_"
	}
	return out
}

func sanitizePathSegment(name string) string {
	if name == "" {
		return "_"
	}
	var b strings.Builder
	for _, r := range name {
		if r == '.' || r == '_' || r == '-' || r == '$' || unicode.IsLetter(r) || unicode.IsDigit(r) {
			b.WriteRune(r)
		} else {
			b.WriteRune('_')
		}
	}
	out := strings.Trim(b.String(), ".")
	if out == "" {
		return "_"
	}
	return out
}

func contains(values []string, target string) bool {
	for _, value := range values {
		if value == target {
			return true
		}
	}
	return false
}

func javaKeyword(s string) bool {
	switch s {
	case "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile", "while":
		return true
	default:
		return false
	}
}
