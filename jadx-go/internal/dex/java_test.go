package dex

import (
	"strings"
	"testing"
)

func TestGuardedReturnBody(t *testing.T) {
	zero := int64(0)
	one := int64(1)
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/Main;", Name: "ready", ReturnType: "Z"},
		AccessFlags: 0x0009,
		Code: CodeInfo{HasCode: true, Registers: 1, Insns: 9, Instructions: []Instruction{
			{Offset: 0, Name: "sget-object", Registers: []int{0}, FieldRef: FieldID{Class: "Lcom/example/Main;", Type: "Landroid/app/Activity;", Name: "activity"}},
			{Offset: 2, Name: "if-eqz", Registers: []int{0}, Target: 12},
			{Offset: 4, Name: "invoke-static", MethodRef: MethodID{Class: "Lcom/google/android/gms/common/GooglePlayServicesUtil;", Name: "isGooglePlayServicesAvailable", ReturnType: "I", Parameters: []string{"Landroid/content/Context;"}}},
			{Offset: 7, Name: "move-result", Registers: []int{0}},
			{Offset: 8, Name: "if-nez", Registers: []int{0}, Target: 12},
			{Offset: 10, Name: "const/4", Registers: []int{0}, IntLiteral: &one},
			{Offset: 11, Name: "return", Registers: []int{0}},
			{Offset: 12, Name: "const/4", Registers: []int{0}, IntLiteral: &zero},
			{Offset: 13, Name: "return", Registers: []int{0}},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if !strings.Contains(got, "decompiled simple guarded return body") || !strings.Contains(got, "return true;") || strings.Contains(got, "UnsupportedOperationException") {
		t.Fatalf("unexpected guarded output:\n%s", got)
	}
}

func TestTryCatchGuardedReturnBody(t *testing.T) {
	zero := int64(0)
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/Main;", Name: "isSignedIn", ReturnType: "Z"},
		AccessFlags: 0x0009,
		Code: CodeInfo{HasCode: true, Registers: 1, Tries: 1, Insns: 13, Instructions: []Instruction{
			{Offset: 0, Name: "sget-object", Registers: []int{0}, FieldRef: FieldID{Class: "Lcom/example/Main;", Type: "Lcom/example/Auth;", Name: "auth"}},
			{Offset: 2, Name: "if-eqz", Registers: []int{0}, Target: 21},
			{Offset: 4, Name: "invoke-virtual", Registers: []int{0}, MethodRef: MethodID{Class: "Lcom/example/Auth;", Name: "getClient", ReturnType: "Lcom/example/Client;"}},
			{Offset: 7, Name: "move-result-object", Registers: []int{0}},
			{Offset: 8, Name: "if-eqz", Registers: []int{0}, Target: 21},
			{Offset: 10, Name: "sget-object", Registers: []int{0}, FieldRef: FieldID{Class: "Lcom/example/Main;", Type: "Lcom/example/Auth;", Name: "auth"}},
			{Offset: 12, Name: "invoke-virtual", Registers: []int{0}, MethodRef: MethodID{Class: "Lcom/example/Auth;", Name: "isSignedIn", ReturnType: "Z"}},
			{Offset: 15, Name: "move-result", Registers: []int{0}},
			{Offset: 16, Name: "return", Registers: []int{0}},
			{Offset: 17, Name: "move-exception", Registers: []int{0}},
			{Offset: 18, Name: "invoke-virtual", Registers: []int{0}, MethodRef: MethodID{Class: "Ljava/lang/Exception;", Name: "printStackTrace", ReturnType: "V"}},
			{Offset: 21, Name: "const/4", Registers: []int{0}, IntLiteral: &zero},
			{Offset: 22, Name: "return", Registers: []int{0}},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if !strings.Contains(got, "decompiled simple try/catch guarded return body") || !strings.Contains(got, "catch (Exception e)") || !strings.Contains(got, "e.printStackTrace();") || strings.Contains(got, "UnsupportedOperationException") {
		t.Fatalf("unexpected try/catch output:\n%s", got)
	}
}

func TestAssignedConditionalReturnBody(t *testing.T) {
	zero := int64(0)
	one := int64(1)
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/Main;", Name: "flag", ReturnType: "Z", Parameters: []string{"Ljava/lang/Object;"}},
		AccessFlags: 0x0009,
		Code: CodeInfo{HasCode: true, Registers: 2, Ins: 1, Insns: 5, Instructions: []Instruction{
			{Offset: 0, Name: "const/4", Registers: []int{0}, IntLiteral: &zero},
			{Offset: 1, Name: "if-eqz", Registers: []int{1}, Target: 4},
			{Offset: 2, Name: "const/4", Registers: []int{0}, IntLiteral: &one},
			{Offset: 4, Name: "return", Registers: []int{0}},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if !strings.Contains(got, "decompiled conditional assigned return") || !strings.Contains(got, "return false;") || !strings.Contains(got, "return true;") || strings.Contains(got, "UnsupportedOperationException") {
		t.Fatalf("unexpected assigned conditional output:\n%s", got)
	}
}

func TestIfGotoReturnBody(t *testing.T) {
	zero := int64(0)
	one := int64(1)
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/Main;", Name: "flagWithGoto", ReturnType: "Z", Parameters: []string{"Ljava/lang/Object;"}},
		AccessFlags: 0x0009,
		Code: CodeInfo{HasCode: true, Registers: 2, Ins: 1, Insns: 7, Instructions: []Instruction{
			{Offset: 0, Name: "if-eqz", Registers: []int{1}, Target: 4},
			{Offset: 1, Name: "const/4", Registers: []int{0}, IntLiteral: &one},
			{Offset: 2, Name: "goto", Target: 5},
			{Offset: 4, Name: "const/4", Registers: []int{0}, IntLiteral: &zero},
			{Offset: 5, Name: "return", Registers: []int{0}},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if !strings.Contains(got, "decompiled simple if/goto return body") || !strings.Contains(got, "return false;") || !strings.Contains(got, "return true;") || strings.Contains(got, "UnsupportedOperationException") {
		t.Fatalf("unexpected if/goto output:\n%s", got)
	}
}

func TestCompareGuardedReturnBody(t *testing.T) {
	zero := int64(0)
	one := int64(1)
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/Main;", Name: "after", ReturnType: "Z", Parameters: []string{"J", "J"}},
		AccessFlags: 0x0009,
		Code: CodeInfo{HasCode: true, Registers: 5, Ins: 4, Insns: 6, Instructions: []Instruction{
			{Offset: 0, Name: "cmp-long", Registers: []int{0, 1, 3}},
			{Offset: 2, Name: "if-lez", Registers: []int{0}, Target: 5},
			{Offset: 3, Name: "const/4", Registers: []int{0}, IntLiteral: &one},
			{Offset: 4, Name: "return", Registers: []int{0}},
			{Offset: 5, Name: "const/4", Registers: []int{0}, IntLiteral: &zero},
			{Offset: 6, Name: "return", Registers: []int{0}},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if !strings.Contains(got, "Long.compare") || !strings.Contains(got, "return true;") || strings.Contains(got, "UnsupportedOperationException") {
		t.Fatalf("unexpected compare output:\n%s", got)
	}
}

func TestInvoke35cCountAndRegisters(t *testing.T) {
	// Format 35c uses A (argument count) in the high nibble of the first code unit
	// high byte, and G in the low nibble. The second code unit stores C,D,E,F.
	high := 0x32 // A=3 args, G=v2
	regs := invoke35cRegisters(high, 0x5431)
	if got := invoke35cCount(high); got != 3 {
		t.Fatalf("wrong 35c count: got %d", got)
	}
	want := []int{1, 3, 4, 5, 2}
	for i := range want {
		if regs[i] != want[i] {
			t.Fatalf("wrong 35c regs: got %#v want %#v", regs, want)
		}
	}
}

func TestInvoke35cLinearReturnRecovery(t *testing.T) {
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/Main;", Name: "appendInt", ReturnType: "Ljava/lang/String;", Parameters: []string{"Ljava/lang/StringBuilder;", "I", "C"}},
		AccessFlags: 0x0009,
		Code: CodeInfo{HasCode: true, Registers: 3, Ins: 3, Insns: 5, Instructions: []Instruction{
			{Offset: 0, Name: "invoke-virtual", Registers: []int{0, 1}, MethodRef: MethodID{Class: "Ljava/lang/StringBuilder;", Name: "append", ReturnType: "Ljava/lang/StringBuilder;", Parameters: []string{"I"}}},
			{Offset: 3, Name: "move-result-object", Registers: []int{0}},
			{Offset: 4, Name: "invoke-virtual", Registers: []int{0, 2}, MethodRef: MethodID{Class: "Ljava/lang/StringBuilder;", Name: "append", ReturnType: "Ljava/lang/StringBuilder;", Parameters: []string{"C"}}},
			{Offset: 7, Name: "move-result-object", Registers: []int{0}},
			{Offset: 8, Name: "invoke-virtual", Registers: []int{0}, MethodRef: MethodID{Class: "Ljava/lang/StringBuilder;", Name: "toString", ReturnType: "Ljava/lang/String;"}},
			{Offset: 11, Name: "move-result-object", Registers: []int{0}},
			{Offset: 12, Name: "return-object", Registers: []int{0}},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if !strings.Contains(got, "p0.append(p1).append(p2).toString()") || strings.Contains(got, "UnsupportedOperationException") {
		t.Fatalf("unexpected 35c linear output:\n%s", got)
	}
}

func TestReferenceAndBooleanZeroConditions(t *testing.T) {
	zero := int64(0)
	one := int64(1)
	objectMethod := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/Main;", Name: "hasObject", ReturnType: "Z", Parameters: []string{"Ljava/lang/Object;"}},
		AccessFlags: 0x0009,
		Code: CodeInfo{HasCode: true, Registers: 2, Ins: 1, Insns: 4, Instructions: []Instruction{
			{Offset: 0, Name: "if-eqz", Registers: []int{1}, Target: 4},
			{Offset: 1, Name: "const/4", Registers: []int{0}, IntLiteral: &one},
			{Offset: 2, Name: "goto", Target: 5},
			{Offset: 4, Name: "const/4", Registers: []int{0}, IntLiteral: &zero},
			{Offset: 5, Name: "return", Registers: []int{0}},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, objectMethod, JavaWriteOptions{})
	if got := b.String(); !strings.Contains(got, "p0 == null") || strings.Contains(got, "p0 == 0") {
		t.Fatalf("expected object null check, got:\n%s", got)
	}

	boolMethod := objectMethod
	boolMethod.ID.Name = "hasFlag"
	boolMethod.ID.Parameters = []string{"Z"}
	b.Reset()
	writeMethodBody(&b, boolMethod, JavaWriteOptions{})
	if got := b.String(); !strings.Contains(got, "!p0") || strings.Contains(got, "p0 == 0") {
		t.Fatalf("expected boolean negation check, got:\n%s", got)
	}
}

func TestDecodeOneInvoke35cUsesArgumentCountNibble(t *testing.T) {
	f := &File{methods: []MethodID{{Class: "Ljava/lang/StringBuilder;", Name: "append", ReturnType: "Ljava/lang/StringBuilder;", Parameters: []string{"I"}}}}
	// word0 high byte 0x20 means A=2 arguments and G=v0. word2 stores C=v1,D=v2,E=v3,F=v4.
	ins := f.decodeOne([]uint16{0x206e, 0x0000, 0x4321}, 0)
	if got, want := ins.Registers, []int{1, 2}; len(got) != len(want) || got[0] != want[0] || got[1] != want[1] {
		t.Fatalf("decodeOne invoke-35c registers = %#v, want %#v", got, want)
	}
}

func TestDecodeOneFilledNewArray35cUsesArgumentCountNibble(t *testing.T) {
	f := &File{types: []string{"[I"}}
	// word0 high byte 0x30 means A=3 arguments and G=v0.
	ins := f.decodeOne([]uint16{0x3024, 0x0000, 0x4321}, 0)
	if got, want := ins.Registers, []int{1, 2, 3}; len(got) != len(want) || got[0] != want[0] || got[1] != want[1] || got[2] != want[2] {
		t.Fatalf("decodeOne filled-new-array registers = %#v, want %#v", got, want)
	}
}

func TestCheckCastReceiverIsParenthesized(t *testing.T) {
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/Main;", Name: "firstFloat", ReturnType: "F", Parameters: []string{"Lkotlin/Pair;"}},
		AccessFlags: 0x0009,
		Code: CodeInfo{HasCode: true, Registers: 2, Ins: 1, Insns: 8, Instructions: []Instruction{
			{Offset: 0, Name: "invoke-virtual", Registers: []int{1}, MethodRef: MethodID{Class: "Lkotlin/Pair;", Name: "getFirst", ReturnType: "Ljava/lang/Object;"}},
			{Offset: 3, Name: "move-result-object", Registers: []int{0}},
			{Offset: 4, Name: "check-cast", Registers: []int{0}, TypeRef: "Ljava/lang/Number;"},
			{Offset: 6, Name: "invoke-virtual", Registers: []int{0}, MethodRef: MethodID{Class: "Ljava/lang/Number;", Name: "floatValue", ReturnType: "F"}},
			{Offset: 9, Name: "move-result", Registers: []int{0}},
			{Offset: 10, Name: "return", Registers: []int{0}},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if !strings.Contains(got, "((java.lang.Number) p0.getFirst()).floatValue()") || strings.Contains(got, "(java.lang.Number) p0.getFirst().floatValue()") {
		t.Fatalf("unexpected check-cast receiver output:\n%s", got)
	}
}

func TestConstructorLinearBodyRecovery(t *testing.T) {
	zero := int64(0)
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/PrefState;", Name: "<init>", ReturnType: "V", Parameters: []string{"Landroid/content/Context;"}},
		AccessFlags: 0x0001,
		Code: CodeInfo{HasCode: true, Registers: 4, Ins: 2, Insns: 12, Instructions: []Instruction{
			{Offset: 0, Name: "invoke-direct", Registers: []int{2}, MethodRef: MethodID{Class: "Ljava/lang/Object;", Name: "<init>", ReturnType: "V"}},
			{Offset: 3, Name: "iput-object", Registers: []int{3, 2}, FieldRef: FieldID{Class: "Lcom/example/PrefState;", Type: "Landroid/content/Context;", Name: "mContext"}},
			{Offset: 5, Name: "const-string", Registers: []int{0}, StringLiteral: "mPrefs"},
			{Offset: 7, Name: "const/4", Registers: []int{1}, IntLiteral: &zero},
			{Offset: 8, Name: "invoke-virtual", Registers: []int{3, 0, 1}, MethodRef: MethodID{Class: "Landroid/content/Context;", Name: "getSharedPreferences", ReturnType: "Landroid/content/SharedPreferences;", Parameters: []string{"Ljava/lang/String;", "I"}}},
			{Offset: 11, Name: "move-result-object", Registers: []int{3}},
			{Offset: 12, Name: "iput-object", Registers: []int{3, 2}, FieldRef: FieldID{Class: "Lcom/example/PrefState;", Type: "Landroid/content/SharedPreferences;", Name: "mPrefs"}},
			{Offset: 14, Name: "return-void"},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if !strings.Contains(got, "super();") || !strings.Contains(got, "this.mPrefs = p0.getSharedPreferences") || strings.Contains(got, "UnsupportedOperationException") {
		t.Fatalf("unexpected constructor output:\n%s", got)
	}
}

func TestDecodeMissingUnaryAndMonitorOpcodes(t *testing.T) {
	f := &File{}
	if got := f.decodeOne([]uint16{0x011d}, 0); got.Name != "monitor-enter" || got.Text != "monitor-enter v1" {
		t.Fatalf("monitor-enter decode = %#v", got)
	}
	if got := f.decodeOne([]uint16{0x021e}, 0); got.Name != "monitor-exit" || got.Text != "monitor-exit v2" {
		t.Fatalf("monitor-exit decode = %#v", got)
	}
	if got := f.decodeOne([]uint16{0x438c}, 0); got.Name != "double-to-float" || got.Text != "double-to-float v3, v4" {
		t.Fatalf("double-to-float decode = %#v", got)
	}
	if got := f.decodeOne([]uint16{0x218d}, 0); got.Name != "int-to-byte" || got.Text != "int-to-byte v1, v2" {
		t.Fatalf("int-to-byte decode = %#v", got)
	}
}

func TestInvokeWideArgumentCollapsesRegisterWords(t *testing.T) {
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/Main;", Name: "seed", ReturnType: "Ljava/lang/String;", Parameters: []string{"J"}},
		AccessFlags: 0x0009,
		Code: CodeInfo{HasCode: true, Registers: 3, Ins: 2, Insns: 5, Instructions: []Instruction{
			{Offset: 0, Name: "invoke-static", Registers: []int{1, 2}, MethodRef: MethodID{Class: "Ljava/lang/String;", Name: "valueOf", ReturnType: "Ljava/lang/String;", Parameters: []string{"J"}}},
			{Offset: 3, Name: "move-result-object", Registers: []int{0}},
			{Offset: 4, Name: "return-object", Registers: []int{0}},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if !strings.Contains(got, "java.lang.String.valueOf(p0)") || strings.Contains(got, "valueOf(p0, p0)") || strings.Contains(got, "valueOf(p0, v2)") {
		t.Fatalf("wide invoke args were not collapsed correctly:\n%s", got)
	}
}

func TestLazyInitReturnBody(t *testing.T) {
	zero := int64(0)
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/Food;", Name: "getName", ReturnType: "Ljava/lang/String;", Parameters: []string{"Landroid/content/Context;"}},
		AccessFlags: 0x0001,
		Code: CodeInfo{HasCode: true, Registers: 3, Ins: 2, Insns: 11, Instructions: []Instruction{
			{Offset: 0, Name: "sget-object", Registers: []int{0}, FieldRef: FieldID{Class: "Lcom/example/Food;", Type: "[Ljava/lang/String;", Name: "sNames"}},
			{Offset: 2, Name: "if-nez", Registers: []int{0}, Target: 17},
			{Offset: 4, Name: "invoke-virtual", Registers: []int{2}, MethodRef: MethodID{Class: "Landroid/content/Context;", Name: "getResources", ReturnType: "Landroid/content/res/Resources;"}},
			{Offset: 7, Name: "move-result-object", Registers: []int{2}},
			{Offset: 8, Name: "const", Registers: []int{0}, IntLiteral: &zero},
			{Offset: 11, Name: "invoke-virtual", Registers: []int{2, 0}, MethodRef: MethodID{Class: "Landroid/content/res/Resources;", Name: "getStringArray", ReturnType: "[Ljava/lang/String;", Parameters: []string{"I"}}},
			{Offset: 14, Name: "move-result-object", Registers: []int{2}},
			{Offset: 15, Name: "sput-object", Registers: []int{2}, FieldRef: FieldID{Class: "Lcom/example/Food;", Type: "[Ljava/lang/String;", Name: "sNames"}},
			{Offset: 17, Name: "sget-object", Registers: []int{2}, FieldRef: FieldID{Class: "Lcom/example/Food;", Type: "[Ljava/lang/String;", Name: "sNames"}},
			{Offset: 19, Name: "aget-object", Registers: []int{1, 2, 1}},
			{Offset: 20, Name: "return-object", Registers: []int{1}},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if !strings.Contains(got, "decompiled simple if/init return body") || !strings.Contains(got, "sNames != null") || strings.Contains(got, "UnsupportedOperationException") {
		t.Fatalf("unexpected lazy init output:\n%s", got)
	}
}

func TestVoidIfGotoBody(t *testing.T) {
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/PrefState;", Name: "setListener", ReturnType: "V", Parameters: []string{"Lcom/example/Listener;"}},
		AccessFlags: 0x0001,
		Code: CodeInfo{HasCode: true, Registers: 2, Ins: 2, Insns: 8, Instructions: []Instruction{
			{Offset: 0, Name: "iput-object", Registers: []int{1, 0}, FieldRef: FieldID{Class: "Lcom/example/PrefState;", Type: "Lcom/example/Listener;", Name: "mListener"}},
			{Offset: 2, Name: "if-eqz", Registers: []int{1}, Target: 10},
			{Offset: 4, Name: "iget-object", Registers: []int{1, 0}, FieldRef: FieldID{Class: "Lcom/example/PrefState;", Type: "Landroid/content/SharedPreferences;", Name: "mPrefs"}},
			{Offset: 6, Name: "invoke-interface", Registers: []int{1, 0}, MethodRef: MethodID{Class: "Landroid/content/SharedPreferences;", Name: "registerOnSharedPreferenceChangeListener", ReturnType: "V", Parameters: []string{"Landroid/content/SharedPreferences$OnSharedPreferenceChangeListener;"}}},
			{Offset: 9, Name: "goto", Target: 15},
			{Offset: 10, Name: "iget-object", Registers: []int{1, 0}, FieldRef: FieldID{Class: "Lcom/example/PrefState;", Type: "Landroid/content/SharedPreferences;", Name: "mPrefs"}},
			{Offset: 12, Name: "invoke-interface", Registers: []int{1, 0}, MethodRef: MethodID{Class: "Landroid/content/SharedPreferences;", Name: "unregisterOnSharedPreferenceChangeListener", ReturnType: "V", Parameters: []string{"Landroid/content/SharedPreferences$OnSharedPreferenceChangeListener;"}}},
			{Offset: 15, Name: "return-void"},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if !strings.Contains(got, "decompiled simple void if/goto body") || !strings.Contains(got, "p0 == null") || !strings.Contains(got, "registerOnSharedPreferenceChangeListener") || strings.Contains(got, "UnsupportedOperationException") {
		t.Fatalf("unexpected void if/goto output:\n%s", got)
	}
}

func TestMonitorOpcodesHaveSingleUnitLength(t *testing.T) {
	f := &File{}
	ins := f.decodeInstructions(0, 0)
	_ = ins
	enter := f.decodeOne([]uint16{0x011d, 0x020e}, 0)
	if enter.Length != 1 {
		t.Fatalf("monitor-enter length = %d, want 1", enter.Length)
	}
}

func TestIgnoredNonVoidInvokeIsEmittedAsStatement(t *testing.T) {
	one := int64(1)
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/Main;", Name: "test", ReturnType: "Z", Parameters: []string{"Ljava/lang/Object;"}},
		AccessFlags: 0x0001,
		Code: CodeInfo{HasCode: true, Registers: 2, Ins: 2, Insns: 5, Instructions: []Instruction{
			{Offset: 0, Name: "check-cast", Registers: []int{1}, TypeRef: "Lcom/example/FeatureFlags;"},
			{Offset: 2, Name: "invoke-interface", Registers: []int{1}, MethodRef: MethodID{Class: "Lcom/example/FeatureFlags;", Name: "flagFlag", ReturnType: "Z"}},
			{Offset: 5, Name: "const/4", Registers: []int{0}, IntLiteral: &one},
			{Offset: 6, Name: "return", Registers: []int{0}},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if !strings.Contains(got, "((com.example.FeatureFlags) p0).flagFlag();") || !strings.Contains(got, "return true;") || strings.Contains(got, "UnsupportedOperationException") {
		t.Fatalf("unexpected ignored invoke output:\n%s", got)
	}
}

func TestJavaTypeKeepsBinarySyntheticNames(t *testing.T) {
	got := javaType("Lcom/example/CustomFeatureFlags$$ExternalSyntheticLambda0;", JavaWriteOptions{})
	if got != "com.example.CustomFeatureFlags$$ExternalSyntheticLambda0" || strings.Contains(got, "..") {
		t.Fatalf("unexpected synthetic type name: %q", got)
	}
}

func TestConstructorPreSuperSyntheticFieldRecovery(t *testing.T) {
	zero := int64(0)
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/Lambda$1;", Name: "<init>", ReturnType: "V", Parameters: []string{"Lcom/example/Owner;"}},
		AccessFlags: 0x0001,
		Code: CodeInfo{HasCode: true, Registers: 2, Ins: 2, Insns: 7, Instructions: []Instruction{
			{Offset: 0, Name: "iput-object", Registers: []int{1, 0}, FieldRef: FieldID{Class: "Lcom/example/Lambda$1;", Type: "Lcom/example/Owner;", Name: "this$0"}},
			{Offset: 2, Name: "const/4", Registers: []int{1}, IntLiteral: &zero},
			{Offset: 3, Name: "invoke-direct", Registers: []int{0, 1}, MethodRef: MethodID{Class: "Lkotlin/jvm/internal/Lambda;", Name: "<init>", ReturnType: "V", Parameters: []string{"I"}}},
			{Offset: 6, Name: "return-void"},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if !strings.Contains(got, "super(0);") || !strings.Contains(got, "this.this$0 = p0;") || strings.Contains(got, "UnsupportedOperationException") {
		t.Fatalf("unexpected pre-super constructor output:\n%s", got)
	}
}

func TestConstructorThisCallWithConstRecovery(t *testing.T) {
	ten := int64(10)
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/List;", Name: "<init>", ReturnType: "V"},
		AccessFlags: 0x0001,
		Code: CodeInfo{HasCode: true, Registers: 2, Ins: 1, Insns: 6, Instructions: []Instruction{
			{Offset: 0, Name: "const/16", Registers: []int{0}, IntLiteral: &ten},
			{Offset: 2, Name: "invoke-direct", Registers: []int{1, 0}, MethodRef: MethodID{Class: "Lcom/example/List;", Name: "<init>", ReturnType: "V", Parameters: []string{"I"}}},
			{Offset: 5, Name: "return-void"},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if !strings.Contains(got, "this(10);") || strings.Contains(got, "UnsupportedOperationException") {
		t.Fatalf("unexpected this-call constructor output:\n%s", got)
	}
}

func TestVoidGuardWithoutGotoBody(t *testing.T) {
	one := int64(1)
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/ViewHook;", Name: "viewCreated", ReturnType: "V", Parameters: []string{"Landroid/view/View;"}},
		AccessFlags: 0x0001,
		Code: CodeInfo{HasCode: true, Registers: 3, Ins: 2, Insns: 15, Instructions: []Instruction{
			{Offset: 0, Name: "iget-boolean", Registers: []int{0, 1}, FieldRef: FieldID{Class: "Lcom/example/ViewHook;", Type: "Z", Name: "onDrawScheduled"}},
			{Offset: 2, Name: "if-nez", Registers: []int{0}, Target: 14},
			{Offset: 4, Name: "const/4", Registers: []int{0}, IntLiteral: &one},
			{Offset: 5, Name: "iput-boolean", Registers: []int{0, 1}, FieldRef: FieldID{Class: "Lcom/example/ViewHook;", Type: "Z", Name: "onDrawScheduled"}},
			{Offset: 7, Name: "invoke-virtual", Registers: []int{2}, MethodRef: MethodID{Class: "Landroid/view/View;", Name: "getViewTreeObserver", ReturnType: "Landroid/view/ViewTreeObserver;"}},
			{Offset: 10, Name: "move-result-object", Registers: []int{2}},
			{Offset: 11, Name: "invoke-virtual", Registers: []int{2, 1}, MethodRef: MethodID{Class: "Landroid/view/ViewTreeObserver;", Name: "addOnDrawListener", ReturnType: "V", Parameters: []string{"Landroid/view/ViewTreeObserver$OnDrawListener;"}}},
			{Offset: 14, Name: "return-void"},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if !strings.Contains(got, "decompiled simple void guard body") || !strings.Contains(got, "if (this.onDrawScheduled)") || !strings.Contains(got, "p0.getViewTreeObserver().addOnDrawListener(this);") || strings.Contains(got, "UnsupportedOperationException") {
		t.Fatalf("unexpected void guard output:\n%s", got)
	}
}

func TestThrowBodyRecovery(t *testing.T) {
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/Main;", Name: "fail", ReturnType: "V", Parameters: []string{"Ljava/lang/String;"}},
		AccessFlags: 0x0009,
		Code: CodeInfo{HasCode: true, Registers: 2, Ins: 1, Insns: 8, Instructions: []Instruction{
			{Offset: 0, Name: "new-instance", Registers: []int{0}, TypeRef: "Ljava/lang/IllegalArgumentException;"},
			{Offset: 2, Name: "invoke-direct", Registers: []int{0, 1}, MethodRef: MethodID{Class: "Ljava/lang/IllegalArgumentException;", Name: "<init>", ReturnType: "V", Parameters: []string{"Ljava/lang/String;"}}},
			{Offset: 5, Name: "throw", Registers: []int{0}},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if !strings.Contains(got, "throw new java.lang.IllegalArgumentException(p0);") || strings.Contains(got, "UnsupportedOperationException") {
		t.Fatalf("unexpected throw output:\n%s", got)
	}
}

func TestIgnoredFluentInvokeChainsReceiverExpression(t *testing.T) {
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/Main;", Name: "describe", ReturnType: "Ljava/lang/String;"},
		AccessFlags: 0x0009,
		Code: CodeInfo{HasCode: true, Registers: 2, Insns: 12, Instructions: []Instruction{
			{Offset: 0, Name: "new-instance", Registers: []int{0}, TypeRef: "Ljava/lang/StringBuilder;"},
			{Offset: 2, Name: "const-string", Registers: []int{1}, StringLiteral: "x="},
			{Offset: 4, Name: "invoke-direct", Registers: []int{0, 1}, MethodRef: MethodID{Class: "Ljava/lang/StringBuilder;", Name: "<init>", ReturnType: "V", Parameters: []string{"Ljava/lang/String;"}}},
			{Offset: 7, Name: "const/4", Registers: []int{1}, IntLiteral: int64Ptr(7)},
			{Offset: 8, Name: "invoke-virtual", Registers: []int{0, 1}, MethodRef: MethodID{Class: "Ljava/lang/StringBuilder;", Name: "append", ReturnType: "Ljava/lang/StringBuilder;", Parameters: []string{"I"}}},
			{Offset: 11, Name: "const-string", Registers: []int{1}, StringLiteral: "!"},
			{Offset: 13, Name: "invoke-virtual", Registers: []int{0, 1}, MethodRef: MethodID{Class: "Ljava/lang/StringBuilder;", Name: "append", ReturnType: "Ljava/lang/StringBuilder;", Parameters: []string{"Ljava/lang/String;"}}},
			{Offset: 16, Name: "invoke-virtual", Registers: []int{0}, MethodRef: MethodID{Class: "Ljava/lang/StringBuilder;", Name: "toString", ReturnType: "Ljava/lang/String;"}},
			{Offset: 19, Name: "move-result-object", Registers: []int{0}},
			{Offset: 20, Name: "return-object", Registers: []int{0}},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if !strings.Contains(got, "new java.lang.StringBuilder(\"x=\").append(7).append(\"!\").toString()") || strings.Count(got, "new java.lang.StringBuilder") != 1 || strings.Contains(got, "UnsupportedOperationException") {
		t.Fatalf("unexpected fluent chain output:\n%s", got)
	}
}

func TestSynchronizedBlockRecovery(t *testing.T) {
	one := int64(1)
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/Main;", Name: "getLocked", ReturnType: "I"},
		AccessFlags: 0x0001,
		Code: CodeInfo{HasCode: true, Registers: 3, Ins: 1, Insns: 8, Instructions: []Instruction{
			{Offset: 0, Name: "iget-object", Registers: []int{0, 2}, FieldRef: FieldID{Class: "Lcom/example/Main;", Type: "Ljava/lang/Object;", Name: "lock"}},
			{Offset: 2, Name: "monitor-enter", Registers: []int{0}},
			{Offset: 3, Name: "const/4", Registers: []int{1}, IntLiteral: &one},
			{Offset: 4, Name: "iput", Registers: []int{1, 2}, FieldRef: FieldID{Class: "Lcom/example/Main;", Type: "I", Name: "value"}},
			{Offset: 6, Name: "monitor-exit", Registers: []int{0}},
			{Offset: 7, Name: "iget", Registers: []int{0, 2}, FieldRef: FieldID{Class: "Lcom/example/Main;", Type: "I", Name: "value"}},
			{Offset: 9, Name: "return", Registers: []int{0}},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if !strings.Contains(got, "decompiled simple synchronized block body") || !strings.Contains(got, "synchronized (this.lock)") || !strings.Contains(got, "this.value = 1;") || !strings.Contains(got, "return this.value;") || strings.Contains(got, "UnsupportedOperationException") {
		t.Fatalf("unexpected synchronized output:\n%s", got)
	}
}

func int64Ptr(v int64) *int64 { return &v }

func TestFillArrayPayloadLengthAndStaticArrayRecovery(t *testing.T) {
	f := &File{}
	// fill-array-data payload: ident=0x0300, width=8, size=2, data={1L, -1L}
	insns := []uint16{
		0x000e, 0x0000,
		0x0300, 0x0008, 0x0002, 0x0000,
		0x0001, 0x0000, 0x0000, 0x0000,
		0xffff, 0xffff, 0xffff, 0xffff,
		0x000e,
	}
	payload := f.decodeOne(insns, 2)
	if payload.Name != "fill-array-data-payload" || payload.Length != 12 || len(payload.ArrayDataValues) != 2 || payload.ArrayDataValues[0] != 1 || payload.ArrayDataValues[1] != -1 {
		t.Fatalf("unexpected payload decode: %#v", payload)
	}
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/Arrays;", Name: "<clinit>", ReturnType: "V"},
		AccessFlags: 0x0008,
		Code: CodeInfo{HasCode: true, Registers: 2, Insns: 16, Instructions: []Instruction{
			{Offset: 0, Name: "const/4", Registers: []int{0}, IntLiteral: int64Ptr(2)},
			{Offset: 1, Name: "new-array", Registers: []int{0, 0}, TypeRef: "[J"},
			{Offset: 3, Name: "fill-array-data", Registers: []int{0}, Target: 6, ArrayDataElementWidth: 8, ArrayDataValues: []int64{1, -1}},
			{Offset: 6, Name: "sput-object", Registers: []int{0}, FieldRef: FieldID{Class: "Lcom/example/Arrays;", Type: "[J", Name: "VALUES"}},
			{Offset: 8, Name: "return-void"},
			{Offset: 9, Name: "fill-array-data-payload", Length: 12, ArrayDataElementWidth: 8, ArrayDataValues: []int64{1, -1}},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if !strings.Contains(got, "VALUES = new long[]{1L, -1L};") || strings.Contains(got, "UnsupportedOperationException") {
		t.Fatalf("unexpected fill-array recovery:\n%s", got)
	}
}

func TestUnsafeRecoveredBodyFallsBackInsteadOfEmittingRawRegisters(t *testing.T) {
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/Main;", Name: "bad", ReturnType: "Ljava/lang/Object;"},
		AccessFlags: 0x0009,
		Code: CodeInfo{HasCode: true, Registers: 2, Ins: 0, Insns: 2, Instructions: []Instruction{
			{Offset: 0, Name: "invoke-virtual", Registers: []int{0}, MethodRef: MethodID{Class: "Ljava/util/Iterator;", Name: "next", ReturnType: "Ljava/lang/Object;"}},
			{Offset: 3, Name: "move-result-object", Registers: []int{1}},
			{Offset: 4, Name: "return-object", Registers: []int{1}},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if strings.Contains(got, "return v") || !strings.Contains(got, "UnsupportedOperationException") {
		t.Fatalf("unsafe recovered body was not rejected:\n%s", got)
	}
}

func TestOpcodeLengthArithmetic23x(t *testing.T) {
	if got := opcodeLength(0xa8); got != 2 {
		t.Fatalf("mul-float length = %d, want 2", got)
	}
	if got := opcodeLength(0x90); got != 2 {
		t.Fatalf("add-int length = %d, want 2", got)
	}
	f := &File{}
	ins := f.decodeInstructions(0, 0)
	_ = ins
}

func TestFloatConstantCoercionForReturnAndArgs(t *testing.T) {
	bits := int64(0x3f800000)
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/Main;", Name: "one", ReturnType: "F"},
		AccessFlags: 0x0009,
		Code: CodeInfo{HasCode: true, Registers: 1, Insns: 2, Instructions: []Instruction{
			{Offset: 0, Name: "const", Registers: []int{0}, IntLiteral: &bits},
			{Offset: 2, Name: "return", Registers: []int{0}},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	if got := b.String(); !strings.Contains(got, "return 1f;") || strings.Contains(got, "1065353216") {
		t.Fatalf("expected float literal coercion, got:\n%s", got)
	}

	callMethod := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/Main;", Name: "call", ReturnType: "F"},
		AccessFlags: 0x0009,
		Code: CodeInfo{HasCode: true, Registers: 1, Insns: 4, Instructions: []Instruction{
			{Offset: 0, Name: "const", Registers: []int{0}, IntLiteral: &bits},
			{Offset: 2, Name: "invoke-static", Registers: []int{0}, MethodRef: MethodID{Class: "Lcom/example/Main;", Name: "id", ReturnType: "F", Parameters: []string{"F"}}},
			{Offset: 5, Name: "move-result", Registers: []int{0}},
			{Offset: 6, Name: "return", Registers: []int{0}},
		}},
	}
	b.Reset()
	writeMethodBody(&b, callMethod, JavaWriteOptions{})
	if got := b.String(); !strings.Contains(got, "id(1f)") || strings.Contains(got, "1065353216") {
		t.Fatalf("expected float argument coercion, got:\n%s", got)
	}
}

func TestDecodePackedAndSparseSwitchPayloads(t *testing.T) {
	f := &File{}
	packed := []uint16{
		0x012b, 0x0006, 0x0000, // packed-switch v1, +6
		0x000e, 0x000e, 0x000e,
		0x0100, 0x0002, 0x0005, 0x0000, // payload: size=2 first_key=5
		0x0003, 0x0000, // key 5 -> pc 3
		0x0004, 0x0000, // key 6 -> pc 4
	}
	ins := f.decodeOne(packed, 0)
	if len(ins.SwitchKeys) != 2 || ins.SwitchKeys[0] != 5 || ins.SwitchKeys[1] != 6 || ins.SwitchTargets[0] != 3 || ins.SwitchTargets[1] != 4 {
		t.Fatalf("packed switch payload not decoded: keys=%#v targets=%#v text=%q", ins.SwitchKeys, ins.SwitchTargets, ins.Text)
	}

	sparse := []uint16{
		0x012c, 0x0006, 0x0000, // sparse-switch v1, +6
		0x000e, 0x000e, 0x000e,
		0x0200, 0x0002, // payload size=2
		0x000a, 0x0000, 0x0014, 0x0000, // keys 10,20
		0x0003, 0x0000, 0x0004, 0x0000, // targets pc 3,4
	}
	ins = f.decodeOne(sparse, 0)
	if len(ins.SwitchKeys) != 2 || ins.SwitchKeys[0] != 10 || ins.SwitchKeys[1] != 20 || ins.SwitchTargets[0] != 3 || ins.SwitchTargets[1] != 4 {
		t.Fatalf("sparse switch payload not decoded: keys=%#v targets=%#v text=%q", ins.SwitchKeys, ins.SwitchTargets, ins.Text)
	}
}

func TestSimpleSwitchReturnBody(t *testing.T) {
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/Synthetic;", Name: "forward", ReturnType: "Ljava/lang/Object;", Parameters: []string{"Ljava/lang/Object;"}},
		AccessFlags: 0x0001,
		Code: CodeInfo{HasCode: true, Registers: 3, Ins: 2, Insns: 10, Instructions: []Instruction{
			{Offset: 0, Name: "iget", Registers: []int{0, 1}, FieldRef: FieldID{Class: "Lcom/example/Synthetic;", Type: "I", Name: "$r8$classId"}},
			{Offset: 2, Name: "packed-switch", Registers: []int{0}, Target: 12, SwitchKeys: []int32{0}, SwitchTargets: []int32{8}},
			{Offset: 5, Name: "iget-object", Registers: []int{0, 2}, FieldRef: FieldID{Class: "Lcom/example/Entry;", Type: "Ljava/lang/Object;", Name: "previous"}},
			{Offset: 7, Name: "return-object", Registers: []int{0}},
			{Offset: 8, Name: "iget-object", Registers: []int{0, 2}, FieldRef: FieldID{Class: "Lcom/example/Entry;", Type: "Ljava/lang/Object;", Name: "next"}},
			{Offset: 10, Name: "return-object", Registers: []int{0}},
			{Offset: 12, Name: "packed-switch-payload"},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if !strings.Contains(got, "decompiled simple switch body") || !strings.Contains(got, "case 0:") || !strings.Contains(got, "return p0.next;") || !strings.Contains(got, "default:") || !strings.Contains(got, "return p0.previous;") || strings.Contains(got, "UnsupportedOperationException") {
		t.Fatalf("unexpected switch output:\n%s", got)
	}
}

func TestIfTerminalElseThrowBody(t *testing.T) {
	one := int64(1)
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/Flags;", Name: "setFlag", ReturnType: "V", Parameters: []string{"Ljava/lang/String;", "Z"}},
		AccessFlags: 0x0001,
		Code: CodeInfo{HasCode: true, Registers: 4, Ins: 3, Insns: 16, Instructions: []Instruction{
			{Offset: 0, Name: "iget-object", Registers: []int{0, 1}, FieldRef: FieldID{Class: "Lcom/example/Flags;", Type: "Ljava/util/Map;", Name: "mFlagMap"}},
			{Offset: 2, Name: "invoke-interface", Registers: []int{0, 2}, MethodRef: MethodID{Class: "Ljava/util/Map;", Name: "containsKey", ReturnType: "Z", Parameters: []string{"Ljava/lang/Object;"}}},
			{Offset: 5, Name: "move-result", Registers: []int{0}},
			{Offset: 6, Name: "if-eqz", Registers: []int{0}, Target: 18},
			{Offset: 8, Name: "iget-object", Registers: []int{1, 1}, FieldRef: FieldID{Class: "Lcom/example/Flags;", Type: "Ljava/util/Map;", Name: "mFlagMap"}},
			{Offset: 10, Name: "invoke-static", Registers: []int{3}, MethodRef: MethodID{Class: "Ljava/lang/Boolean;", Name: "valueOf", ReturnType: "Ljava/lang/Boolean;", Parameters: []string{"Z"}}},
			{Offset: 13, Name: "move-result-object", Registers: []int{3}},
			{Offset: 14, Name: "invoke-interface", Registers: []int{1, 2, 3}, MethodRef: MethodID{Class: "Ljava/util/Map;", Name: "put", ReturnType: "Ljava/lang/Object;", Parameters: []string{"Ljava/lang/Object;", "Ljava/lang/Object;"}}},
			{Offset: 17, Name: "return-void"},
			{Offset: 18, Name: "new-instance", Registers: []int{1}, TypeRef: "Ljava/lang/IllegalArgumentException;"},
			{Offset: 20, Name: "const-string", Registers: []int{3}, StringLiteral: "no such flag"},
			{Offset: 22, Name: "invoke-direct", Registers: []int{1, 3}, MethodRef: MethodID{Class: "Ljava/lang/IllegalArgumentException;", Name: "<init>", ReturnType: "V", Parameters: []string{"Ljava/lang/String;"}}},
			{Offset: 25, Name: "throw", Registers: []int{1}},
			{Offset: 26, Name: "const/4", Registers: []int{3}, IntLiteral: &one},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if !strings.Contains(got, "decompiled simple if/else terminal body") || !strings.Contains(got, "throw new java.lang.IllegalArgumentException") || strings.Contains(got, "UnsupportedOperationException") {
		t.Fatalf("unexpected if terminal/throw output:\n%s", got)
	}
}

func TestConditionalAssignmentThenLinearReturnBody(t *testing.T) {
	zero := int64(0)
	one := int64(1)
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/Result;", Name: "label", ReturnType: "Ljava/lang/String;", Parameters: []string{"I"}},
		AccessFlags: 0x0009,
		Code: CodeInfo{HasCode: true, Registers: 3, Ins: 1, Insns: 18, Instructions: []Instruction{
			{Offset: 0, Name: "const/4", Registers: []int{0}, IntLiteral: &zero},
			{Offset: 1, Name: "if-eq", Registers: []int{2, 0}, Target: 9},
			{Offset: 3, Name: "invoke-static", Registers: []int{2}, MethodRef: MethodID{Class: "Ljava/lang/String;", Name: "valueOf", ReturnType: "Ljava/lang/String;", Parameters: []string{"I"}}},
			{Offset: 6, Name: "move-result-object", Registers: []int{1}},
			{Offset: 7, Name: "goto", Target: 11},
			{Offset: 9, Name: "const-string", Registers: []int{1}, StringLiteral: "ZERO"},
			{Offset: 11, Name: "new-instance", Registers: []int{0}, TypeRef: "Ljava/lang/StringBuilder;"},
			{Offset: 13, Name: "const-string", Registers: []int{2}, StringLiteral: "value="},
			{Offset: 15, Name: "invoke-direct", Registers: []int{0, 2}, MethodRef: MethodID{Class: "Ljava/lang/StringBuilder;", Name: "<init>", ReturnType: "V", Parameters: []string{"Ljava/lang/String;"}}},
			{Offset: 18, Name: "invoke-virtual", Registers: []int{0, 1}, MethodRef: MethodID{Class: "Ljava/lang/StringBuilder;", Name: "append", ReturnType: "Ljava/lang/StringBuilder;", Parameters: []string{"Ljava/lang/String;"}}},
			{Offset: 21, Name: "move-result-object", Registers: []int{0}},
			{Offset: 22, Name: "invoke-virtual", Registers: []int{0}, MethodRef: MethodID{Class: "Ljava/lang/StringBuilder;", Name: "toString", ReturnType: "Ljava/lang/String;"}},
			{Offset: 25, Name: "move-result-object", Registers: []int{0}},
			{Offset: 26, Name: "return-object", Registers: []int{0}},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if !strings.Contains(got, "p0 == 0 ? \"ZERO\" : java.lang.String.valueOf(p0)") || !strings.Contains(got, "append") || strings.Contains(got, "UnsupportedOperationException") {
		t.Fatalf("unexpected conditional assignment output:\n%s", got)
	}
	_ = one
}

func TestMultiBranchAssignmentLinearTail(t *testing.T) {
	minusOne := int64(-1)
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/ActivityResult;", Name: "toString", ReturnType: "Ljava/lang/String;"},
		AccessFlags: 0x0001,
		Code: CodeInfo{HasCode: true, Registers: 4, Ins: 1, Insns: 22, Instructions: []Instruction{
			{Offset: 0, Name: "new-instance", Registers: []int{0}, TypeRef: "Ljava/lang/StringBuilder;"},
			{Offset: 2, Name: "const-string", Registers: []int{1}, StringLiteral: "ActivityResult{resultCode="},
			{Offset: 4, Name: "invoke-direct", Registers: []int{0, 1}, MethodRef: MethodID{Class: "Ljava/lang/StringBuilder;", Name: "<init>", ReturnType: "V", Parameters: []string{"Ljava/lang/String;"}}},
			{Offset: 7, Name: "iget", Registers: []int{1, 3}, FieldRef: FieldID{Class: "Lcom/example/ActivityResult;", Type: "I", Name: "resultCode"}},
			{Offset: 9, Name: "const/4", Registers: []int{2}, IntLiteral: &minusOne},
			{Offset: 10, Name: "if-eq", Registers: []int{1, 2}, Target: 22},
			{Offset: 12, Name: "if-eqz", Registers: []int{1}, Target: 19},
			{Offset: 14, Name: "invoke-static", Registers: []int{1}, MethodRef: MethodID{Class: "Ljava/lang/String;", Name: "valueOf", ReturnType: "Ljava/lang/String;", Parameters: []string{"I"}}},
			{Offset: 17, Name: "move-result-object", Registers: []int{1}},
			{Offset: 18, Name: "goto", Target: 24},
			{Offset: 19, Name: "const-string", Registers: []int{1}, StringLiteral: "RESULT_CANCELED"},
			{Offset: 21, Name: "goto", Target: 24},
			{Offset: 22, Name: "const-string", Registers: []int{1}, StringLiteral: "RESULT_OK"},
			{Offset: 24, Name: "invoke-virtual", Registers: []int{0, 1}, MethodRef: MethodID{Class: "Ljava/lang/StringBuilder;", Name: "append", ReturnType: "Ljava/lang/StringBuilder;", Parameters: []string{"Ljava/lang/String;"}}},
			{Offset: 27, Name: "move-result-object", Registers: []int{0}},
			{Offset: 28, Name: "const-string", Registers: []int{1}, StringLiteral: "}"},
			{Offset: 30, Name: "invoke-virtual", Registers: []int{0, 1}, MethodRef: MethodID{Class: "Ljava/lang/StringBuilder;", Name: "append", ReturnType: "Ljava/lang/StringBuilder;", Parameters: []string{"Ljava/lang/String;"}}},
			{Offset: 33, Name: "move-result-object", Registers: []int{0}},
			{Offset: 34, Name: "invoke-virtual", Registers: []int{0}, MethodRef: MethodID{Class: "Ljava/lang/StringBuilder;", Name: "toString", ReturnType: "Ljava/lang/String;"}},
			{Offset: 37, Name: "move-result-object", Registers: []int{0}},
			{Offset: 38, Name: "return-object", Registers: []int{0}},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if !strings.Contains(got, "decompiled multi-branch assignment body") || !strings.Contains(got, "RESULT_OK") || !strings.Contains(got, "RESULT_CANCELED") || !strings.Contains(got, "String.valueOf") || strings.Contains(got, "UnsupportedOperationException") {
		t.Fatalf("unexpected multi-branch output:\n%s", got)
	}
}

func TestConditionalAssignmentThenVoidGuardTail(t *testing.T) {
	zero := int64(0)
	one := int64(1)
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/ActivityResult;", Name: "writeToParcel", ReturnType: "V", Parameters: []string{"Landroid/os/Parcel;", "I"}},
		AccessFlags: 0x0001,
		Code: CodeInfo{HasCode: true, Registers: 4, Ins: 3, Insns: 15, Instructions: []Instruction{
			{Offset: 0, Name: "iget-object", Registers: []int{0, 1}, FieldRef: FieldID{Class: "Lcom/example/ActivityResult;", Type: "Landroid/content/Intent;", Name: "data"}},
			{Offset: 2, Name: "if-nez", Registers: []int{0}, Target: 8},
			{Offset: 4, Name: "const/4", Registers: []int{0}, IntLiteral: &zero},
			{Offset: 5, Name: "goto", Target: 9},
			{Offset: 8, Name: "const/4", Registers: []int{0}, IntLiteral: &one},
			{Offset: 9, Name: "invoke-virtual", Registers: []int{2, 0}, MethodRef: MethodID{Class: "Landroid/os/Parcel;", Name: "writeInt", ReturnType: "V", Parameters: []string{"I"}}},
			{Offset: 12, Name: "iget-object", Registers: []int{1, 1}, FieldRef: FieldID{Class: "Lcom/example/ActivityResult;", Type: "Landroid/content/Intent;", Name: "data"}},
			{Offset: 14, Name: "if-eqz", Registers: []int{1}, Target: 23},
			{Offset: 16, Name: "invoke-virtual", Registers: []int{1, 2, 3}, MethodRef: MethodID{Class: "Landroid/content/Intent;", Name: "writeToParcel", ReturnType: "V", Parameters: []string{"Landroid/os/Parcel;", "I"}}},
			{Offset: 23, Name: "return-void"},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if !strings.Contains(got, "decompiled multi-branch assignment body") || !strings.Contains(got, "writeInt") || !strings.Contains(got, "writeToParcel") || strings.Contains(got, "UnsupportedOperationException") {
		t.Fatalf("unexpected conditional void tail output:\n%s", got)
	}
}

func TestOwnerAwareReceiverRecoveryAvoidsStaleFieldChain(t *testing.T) {
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/ActivityResult;", Name: "reloadData", ReturnType: "Landroid/content/Intent;"},
		AccessFlags: 0x0001,
		Code: CodeInfo{HasCode: true, Registers: 2, Ins: 1, Insns: 5, Instructions: []Instruction{
			{Offset: 0, Name: "iget-object", Registers: []int{1, 1}, FieldRef: FieldID{Class: "Lcom/example/ActivityResult;", Type: "Landroid/content/Intent;", Name: "data"}},
			{Offset: 2, Name: "iget-object", Registers: []int{1, 1}, FieldRef: FieldID{Class: "Lcom/example/ActivityResult;", Type: "Landroid/content/Intent;", Name: "data"}},
			{Offset: 4, Name: "return-object", Registers: []int{1}},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if !strings.Contains(got, "return this.data;") || strings.Contains(got, "this.data.data") || strings.Contains(got, "UnsupportedOperationException") {
		t.Fatalf("unexpected owner-aware receiver output:\n%s", got)
	}
}

func TestOwnerAwareReceiverRecoveryAvoidsStaleMethodChain(t *testing.T) {
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/ActivityResult;", Name: "writeData", ReturnType: "V", Parameters: []string{"Landroid/os/Parcel;", "I"}},
		AccessFlags: 0x0001,
		Code: CodeInfo{HasCode: true, Registers: 4, Ins: 3, Insns: 8, Instructions: []Instruction{
			{Offset: 0, Name: "iget-object", Registers: []int{1, 1}, FieldRef: FieldID{Class: "Lcom/example/ActivityResult;", Type: "Landroid/content/Intent;", Name: "data"}},
			{Offset: 2, Name: "invoke-virtual", Registers: []int{1, 2, 3}, MethodRef: MethodID{Class: "Lcom/example/ActivityResult;", Name: "touch", ReturnType: "V", Parameters: []string{"Landroid/os/Parcel;", "I"}}},
			{Offset: 5, Name: "return-void"},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if !strings.Contains(got, "this.touch(p0, p1);") || strings.Contains(got, "this.data.touch") || strings.Contains(got, "UnsupportedOperationException") {
		t.Fatalf("unexpected owner-aware method output:\n%s", got)
	}
}

func TestFieldWritesCoerceBooleanAndNullLiterals(t *testing.T) {
	zero := int64(0)
	one := int64(1)
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/Main;", Name: "update", ReturnType: "V"},
		AccessFlags: 0x0001,
		Code: CodeInfo{HasCode: true, Registers: 2, Ins: 1, Insns: 7, Instructions: []Instruction{
			{Offset: 0, Name: "const/4", Registers: []int{0}, IntLiteral: &one},
			{Offset: 1, Name: "iput-boolean", Registers: []int{0, 1}, FieldRef: FieldID{Class: "Lcom/example/Main;", Type: "Z", Name: "ready"}},
			{Offset: 3, Name: "const/4", Registers: []int{0}, IntLiteral: &zero},
			{Offset: 4, Name: "iput-object", Registers: []int{0, 1}, FieldRef: FieldID{Class: "Lcom/example/Main;", Type: "Ljava/lang/Object;", Name: "value"}},
			{Offset: 6, Name: "return-void"},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if !strings.Contains(got, "this.ready = true;") || !strings.Contains(got, "this.value = null;") || strings.Contains(got, "this.ready = 1;") || strings.Contains(got, "this.value = 0;") {
		t.Fatalf("field literal coercion failed:\n%s", got)
	}
}

func TestArrayPutCoercesBooleanAndNullLiterals(t *testing.T) {
	zero := int64(0)
	one := int64(1)
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/Main;", Name: "arrays", ReturnType: "V"},
		AccessFlags: 0x0009,
		Code: CodeInfo{HasCode: true, Registers: 5, Ins: 0, Insns: 12, Instructions: []Instruction{
			{Offset: 0, Name: "const/4", Registers: []int{0}, IntLiteral: &one},
			{Offset: 1, Name: "new-array", Registers: []int{1, 0}, TypeRef: "[Z"},
			{Offset: 3, Name: "const/4", Registers: []int{2}, IntLiteral: &zero},
			{Offset: 4, Name: "aput-boolean", Registers: []int{0, 1, 2}},
			{Offset: 6, Name: "new-array", Registers: []int{3, 0}, TypeRef: "[Ljava/lang/Object;"},
			{Offset: 8, Name: "aput-object", Registers: []int{2, 3, 2}},
			{Offset: 10, Name: "return-void"},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if !strings.Contains(got, "boolean[] array1 = new boolean[1];") || !strings.Contains(got, "array1[0] = true;") || !strings.Contains(got, "java.lang.Object[] array3 = new java.lang.Object[1];") || !strings.Contains(got, "array3[0] = null;") || strings.Contains(got, "new boolean[1][0]") || strings.Contains(got, "new java.lang.Object[1][0]") {
		t.Fatalf("array literal coercion failed:\n%s", got)
	}
}

func TestNoRegisterObjectConstructorRecovery(t *testing.T) {
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/Main;", Name: "<init>", ReturnType: "V"},
		AccessFlags: 0x0001,
		Code: CodeInfo{HasCode: true, Registers: 1, Ins: 1, Insns: 4, Instructions: []Instruction{
			{Offset: 0, Name: "invoke-direct", MethodRef: MethodID{Class: "Ljava/lang/Object;", Name: "<init>", ReturnType: "V"}},
			{Offset: 3, Name: "return-void"},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if !strings.Contains(got, "super();") || strings.Contains(got, "UnsupportedOperationException") {
		t.Fatalf("constructor recovery failed:\n%s", got)
	}
}

func TestDescriptorPreservingMoveAvoidsNullPrimitiveCheck(t *testing.T) {
	zero := int64(0)
	one := int64(1)
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/Main;", Name: "isVisible", ReturnType: "Z", Parameters: []string{"Landroid/view/View;"}},
		AccessFlags: 0x0009,
		Code: CodeInfo{HasCode: true, Registers: 3, Ins: 1, Insns: 8, Instructions: []Instruction{
			{Offset: 0, Name: "invoke-virtual", Registers: []int{2}, MethodRef: MethodID{Class: "Landroid/view/View;", Name: "getVisibility", ReturnType: "I"}},
			{Offset: 3, Name: "move-result", Registers: []int{0}},
			{Offset: 4, Name: "move", Registers: []int{1, 0}},
			{Offset: 5, Name: "if-eqz", Registers: []int{1}, Target: 9},
			{Offset: 6, Name: "const/4", Registers: []int{0}, IntLiteral: &zero},
			{Offset: 7, Name: "return", Registers: []int{0}},
			{Offset: 9, Name: "const/4", Registers: []int{0}, IntLiteral: &one},
			{Offset: 10, Name: "return", Registers: []int{0}},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if !strings.Contains(got, "p0.getVisibility() == 0") || strings.Contains(got, "getVisibility() == null") || strings.Contains(got, "UnsupportedOperationException") {
		t.Fatalf("primitive descriptor was not preserved through move:\n%s", got)
	}
}

func TestRepeatedNewArrayLocalNamesStayUnique(t *testing.T) {
	two := int64(2)
	three := int64(3)
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/Arrays;", Name: "<clinit>", ReturnType: "V"},
		AccessFlags: 0x0008,
		Code: CodeInfo{HasCode: true, Registers: 1, Insns: 8, Instructions: []Instruction{
			{Offset: 0, Name: "const/4", Registers: []int{0}, IntLiteral: &two},
			{Offset: 1, Name: "new-array", Registers: []int{0, 0}, TypeRef: "[I"},
			{Offset: 3, Name: "sput-object", Registers: []int{0}, FieldRef: FieldID{Class: "Lcom/example/Arrays;", Type: "[I", Name: "FIRST"}},
			{Offset: 5, Name: "const/4", Registers: []int{0}, IntLiteral: &three},
			{Offset: 6, Name: "new-array", Registers: []int{0, 0}, TypeRef: "[I"},
			{Offset: 8, Name: "sput-object", Registers: []int{0}, FieldRef: FieldID{Class: "Lcom/example/Arrays;", Type: "[I", Name: "SECOND"}},
			{Offset: 10, Name: "return-void"},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if strings.Count(got, "int[] array0 =") != 1 || !strings.Contains(got, "int[] array0_1 = new int[3];") || !strings.Contains(got, "FIRST = array0;") || !strings.Contains(got, "SECOND = array0_1;") || strings.Contains(got, "UnsupportedOperationException") {
		t.Fatalf("repeated array locals were not unique:\n%s", got)
	}
}

func TestConditionExpressionTwoRegisterZeroCoercion(t *testing.T) {
	zero := int64(0)
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/Main;", Name: "twoRegChecks", ReturnType: "Z", Parameters: []string{"Ljava/lang/Object;", "Z", "I"}},
		AccessFlags: 0x0008,
		Code:        CodeInfo{HasCode: true, Registers: 4, Ins: 3},
	}
	values := map[int]string{
		0: constExpression(Instruction{Name: "const/4", Registers: []int{0}, IntLiteral: &zero}),
		1: typedExpression("p0", "Ljava/lang/Object;"),
		2: typedExpression("p1", "Z"),
		3: typedExpression("p2", "I"),
	}
	objCond := conditionExpression(method, Instruction{Name: "if-eq", Registers: []int{1, 0}}, JavaWriteOptions{}, values)
	boolCond := conditionExpression(method, Instruction{Name: "if-ne", Registers: []int{2, 0}}, JavaWriteOptions{}, values)
	intCond := conditionExpression(method, Instruction{Name: "if-eq", Registers: []int{3, 0}}, JavaWriteOptions{}, values)
	if objCond != "p0 == null" || boolCond != "p1" || intCond != "p2 == 0" {
		t.Fatalf("unexpected coerced comparisons: obj=%q bool=%q int=%q", objCond, boolCond, intCond)
	}
}

func TestPrimitiveMethodZeroConditionDoesNotUseNull(t *testing.T) {
	zero := int64(0)
	one := int64(1)
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/Main;", Name: "empty", ReturnType: "Z", Parameters: []string{"Ljava/util/List;"}},
		AccessFlags: 0x0009,
		Code: CodeInfo{HasCode: true, Registers: 2, Ins: 1, Insns: 7, Instructions: []Instruction{
			{Offset: 0, Name: "invoke-interface", Registers: []int{1}, MethodRef: MethodID{Class: "Ljava/util/List;", Name: "size", ReturnType: "I"}},
			{Offset: 3, Name: "move-result", Registers: []int{0}},
			{Offset: 4, Name: "if-nez", Registers: []int{0}, Target: 8},
			{Offset: 5, Name: "const/4", Registers: []int{0}, IntLiteral: &one},
			{Offset: 6, Name: "goto", Target: 9},
			{Offset: 8, Name: "const/4", Registers: []int{0}, IntLiteral: &zero},
			{Offset: 9, Name: "return", Registers: []int{0}},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if strings.Contains(got, "null") || !strings.Contains(got, "p0.size() != 0") || !strings.Contains(got, "? false : true") {
		t.Fatalf("expected primitive size comparison against 0 and boolean ternary coercion, got:\n%s", got)
	}
}

func TestBooleanNumericTernaryReturnCoercion(t *testing.T) {
	got := returnExpressionForType("(p0.size() != 0 ? 0 : 1)", "boolean")
	if got != "(p0.size() != 0 ? false : true)" {
		t.Fatalf("unexpected boolean ternary coercion: %s", got)
	}
}

func TestTypedBitwiseZeroConditionDoesNotUseNull(t *testing.T) {
	zero := int64(0)
	one := int64(1)
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/State;", Name: "isRead", ReturnType: "Z", Parameters: []string{"I"}},
		AccessFlags: 0x0001,
		Code: CodeInfo{HasCode: true, Registers: 3, Ins: 2, Insns: 8, Instructions: []Instruction{
			{Offset: 0, Name: "const/4", Registers: []int{0}, IntLiteral: &one},
			{Offset: 1, Name: "and-int", Registers: []int{0, 0, 2}},
			{Offset: 3, Name: "if-eqz", Registers: []int{0}, Target: 7},
			{Offset: 4, Name: "const/4", Registers: []int{0}, IntLiteral: &one},
			{Offset: 5, Name: "goto", Target: 8},
			{Offset: 7, Name: "const/4", Registers: []int{0}, IntLiteral: &zero},
			{Offset: 8, Name: "return", Registers: []int{0}},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if !strings.Contains(got, "(1 & p0) == 0") || strings.Contains(got, "== null") || strings.Contains(got, "UnsupportedOperationException") {
		t.Fatalf("expected bitwise comparison against 0, got:\n%s", got)
	}
}

func TestBooleanTernaryAllowsBooleanBranch(t *testing.T) {
	got := returnExpressionForType("(p0 == null ? 1 : p0.isActive())", "boolean")
	if got != "(p0 == null ? true : p0.isActive())" {
		t.Fatalf("unexpected mixed boolean ternary coercion: %s", got)
	}
}

func TestBooleanArgumentTernaryCoercion(t *testing.T) {
	got := expressionForDescriptor("(p0 != null ? 0 : 1)", "Z")
	if got != "(p0 != null ? false : true)" {
		t.Fatalf("boolean argument ternary was not coerced: %s", got)
	}
}

func TestThrowZeroUsesNullLiteral(t *testing.T) {
	zero := int64(0)
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/Main;", Name: "badThrow", ReturnType: "V"},
		AccessFlags: 0x0009,
		Code: CodeInfo{HasCode: true, Registers: 1, Insns: 2, Instructions: []Instruction{
			{Offset: 0, Name: "const/4", Registers: []int{0}, IntLiteral: &zero},
			{Offset: 1, Name: "throw", Registers: []int{0}},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if !strings.Contains(got, "throw null;") || strings.Contains(got, "throw 0;") || strings.Contains(got, "UnsupportedOperationException") {
		t.Fatalf("throw-zero was not converted to null:\n%s", got)
	}
}

func TestOverwrittenThisRegisterBitwiseDoesNotUseNull(t *testing.T) {
	zero := int64(0)
	one := int64(1)
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/ViewHolder;", Name: "hasFlags", ReturnType: "Z", Parameters: []string{"I"}},
		AccessFlags: 0x0001,
		Code: CodeInfo{HasCode: true, Registers: 2, Ins: 2, Insns: 8, Instructions: []Instruction{
			{Offset: 0, Name: "iget", Registers: []int{0, 0}, FieldRef: FieldID{Class: "Lcom/example/ViewHolder;", Type: "I", Name: "mFlags"}},
			{Offset: 2, Name: "and-int/2addr", Registers: []int{0, 1}},
			{Offset: 3, Name: "if-eqz", Registers: []int{0}, Target: 7},
			{Offset: 4, Name: "const/4", Registers: []int{0}, IntLiteral: &one},
			{Offset: 5, Name: "goto", Target: 8},
			{Offset: 7, Name: "const/4", Registers: []int{0}, IntLiteral: &zero},
			{Offset: 8, Name: "return", Registers: []int{0}},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if !strings.Contains(got, "(this.mFlags & p0) == 0") || strings.Contains(got, "== null") || strings.Contains(got, "UnsupportedOperationException") {
		t.Fatalf("overwritten this-register bitwise condition used the wrong comparison:\n%s", got)
	}
}

func TestIfFallthroughAssignReturnCoalesces(t *testing.T) {
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/Coalesce;", Name: "choose", ReturnType: "Ljava/lang/Object;", Parameters: []string{"Ljava/lang/Object;", "Ljava/lang/Object;"}},
		AccessFlags: 0x0001,
		Code: CodeInfo{HasCode: true, Registers: 3, Ins: 3, Insns: 4, Instructions: []Instruction{
			{Offset: 0, Name: "if-nez", Registers: []int{1}, Target: 3},
			{Offset: 2, Name: "move-object", Registers: []int{1, 2}},
			{Offset: 3, Name: "return-object", Registers: []int{1}},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if !strings.Contains(got, "return (p0 != null ? p0 : p1);") || strings.Contains(got, "UnsupportedOperationException") {
		t.Fatalf("branch-to-return fallback assignment was not recovered:\n%s", got)
	}
}

func TestNestedGotoConditionalAssignmentTailRecovery(t *testing.T) {
	zero := int64(0)
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/Iterator;", Name: "next", ReturnType: "Lcom/example/Entry;"},
		AccessFlags: 0x0001,
		Code: CodeInfo{HasCode: true, Registers: 3, Ins: 1, Insns: 18, Instructions: []Instruction{
			{Offset: 0, Name: "iget-object", Registers: []int{0, 2}, FieldRef: FieldID{Class: "Lcom/example/Iterator;", Type: "Lcom/example/Entry;", Name: "mNext"}},
			{Offset: 2, Name: "iget-object", Registers: []int{1, 2}, FieldRef: FieldID{Class: "Lcom/example/Iterator;", Type: "Lcom/example/Entry;", Name: "mExpectedEnd"}},
			{Offset: 4, Name: "if-eq", Registers: []int{0, 1}, Target: 14},
			{Offset: 6, Name: "if-nez", Registers: []int{1}, Target: 9},
			{Offset: 8, Name: "goto", Target: 14},
			{Offset: 9, Name: "invoke-virtual", Registers: []int{2, 0}, MethodRef: MethodID{Class: "Lcom/example/Iterator;", Name: "forward", ReturnType: "Lcom/example/Entry;", Parameters: []string{"Lcom/example/Entry;"}}},
			{Offset: 12, Name: "move-result-object", Registers: []int{1}},
			{Offset: 13, Name: "goto", Target: 15},
			{Offset: 14, Name: "const/4", Registers: []int{1}, IntLiteral: &zero},
			{Offset: 15, Name: "iput-object", Registers: []int{1, 2}, FieldRef: FieldID{Class: "Lcom/example/Iterator;", Type: "Lcom/example/Entry;", Name: "mNext"}},
			{Offset: 17, Name: "return-object", Registers: []int{0}},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if !strings.Contains(got, "decompiled multi-branch assignment body") || !strings.Contains(got, "this.mNext =") || !strings.Contains(got, "this.forward(this.mNext)") || strings.Contains(got, "UnsupportedOperationException") {
		t.Fatalf("unexpected nested conditional assignment output:\n%s", got)
	}
}

func TestSwitchCaseWithGuardedVoidTailRecovery(t *testing.T) {
	method := EncodedMethod{
		ID:          MethodID{Class: "Lcom/example/Observer;", Name: "onStateChanged", ReturnType: "V", Parameters: []string{"Lcom/example/Owner;", "Lcom/example/Event;"}},
		AccessFlags: 0x0001,
		Code: CodeInfo{HasCode: true, Registers: 4, Ins: 3, Insns: 40, Instructions: []Instruction{
			{Offset: 0, Name: "iget", Registers: []int{0, 1}, FieldRef: FieldID{Class: "Lcom/example/Observer;", Type: "I", Name: "$r8$classId"}},
			{Offset: 2, Name: "packed-switch", Registers: []int{0}, SwitchKeys: []int32{0}, SwitchTargets: []int32{11}},
			{Offset: 5, Name: "iget-object", Registers: []int{1, 1}, FieldRef: FieldID{Class: "Lcom/example/Observer;", Type: "Lcom/example/Activity;", Name: "f$0"}},
			{Offset: 7, Name: "invoke-static", Registers: []int{1, 2, 3}, MethodRef: MethodID{Class: "Lcom/example/Observer;", Name: "defaultAction", ReturnType: "V", Parameters: []string{"Lcom/example/Activity;", "Lcom/example/Owner;", "Lcom/example/Event;"}}},
			{Offset: 10, Name: "return-void"},
			{Offset: 11, Name: "iget-object", Registers: []int{1, 1}, FieldRef: FieldID{Class: "Lcom/example/Observer;", Type: "Lcom/example/Activity;", Name: "f$0"}},
			{Offset: 13, Name: "sget-object", Registers: []int{2}, FieldRef: FieldID{Class: "Lcom/example/Event;", Type: "Lcom/example/Event;", Name: "ON_STOP"}},
			{Offset: 15, Name: "if-ne", Registers: []int{3, 2}, Target: 30},
			{Offset: 17, Name: "invoke-virtual", Registers: []int{1}, MethodRef: MethodID{Class: "Lcom/example/Activity;", Name: "getWindow", ReturnType: "Lcom/example/Window;"}},
			{Offset: 20, Name: "move-result-object", Registers: []int{1}},
			{Offset: 21, Name: "if-eqz", Registers: []int{1}, Target: 30},
			{Offset: 23, Name: "invoke-virtual", Registers: []int{1}, MethodRef: MethodID{Class: "Lcom/example/Window;", Name: "peekDecorView", ReturnType: "Lcom/example/View;"}},
			{Offset: 26, Name: "move-result-object", Registers: []int{1}},
			{Offset: 27, Name: "if-eqz", Registers: []int{1}, Target: 30},
			{Offset: 29, Name: "invoke-virtual", Registers: []int{1}, MethodRef: MethodID{Class: "Lcom/example/View;", Name: "cancelPendingInputEvents", ReturnType: "V"}},
			{Offset: 30, Name: "return-void"},
		}},
	}
	var b strings.Builder
	writeMethodBody(&b, method, JavaWriteOptions{})
	got := b.String()
	if !strings.Contains(got, "decompiled simple switch body") || !strings.Contains(got, "case 0:") || !strings.Contains(got, "cancelPendingInputEvents();") || strings.Contains(got, "UnsupportedOperationException") {
		t.Fatalf("unexpected guarded switch output:\n%s", got)
	}
}

func TestRecoveredBodyRejectsDegenerateTernary(t *testing.T) {
	bad := "        // jadx-go: decompiled multi-branch assignment body.\n        return (p0 == null ? true : true);\n"
	if recoveredJavaBodyLooksSafe(bad) {
		t.Fatalf("expected degenerate ternary recovery to be rejected")
	}
	good := "        return (p0 == null ? true : false);\n"
	if !recoveredJavaBodyLooksSafe(good) {
		t.Fatalf("expected meaningful ternary recovery to remain allowed")
	}
}
