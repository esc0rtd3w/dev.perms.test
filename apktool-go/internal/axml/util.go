package axml

import (
	"bytes"
	"encoding/binary"
	"fmt"
	"strings"
)

func le16(b []byte) uint16 { return binary.LittleEndian.Uint16(b) }
func le32(b []byte) uint32 { return binary.LittleEndian.Uint32(b) }

type namespaceDecl struct {
	prefix  string
	uri     string
	written bool
}

type namespaceStack struct {
	decls     []namespaceDecl
	generated map[string]string
}

func newNamespaceStack() *namespaceStack {
	return &namespaceStack{generated: make(map[string]string)}
}

func (s *namespaceStack) push(prefix, uri string) {
	if uri == "" {
		return
	}
	if uri == "http://schemas.android.com/apk/res/android" && prefix == "" {
		prefix = "android"
	}
	if prefix == "" && uri != "" {
		if existing, ok := s.generated[uri]; ok {
			prefix = existing
		} else {
			prefix = fmt.Sprintf("ns%d", len(s.generated)+1)
			s.generated[uri] = prefix
		}
	}
	s.decls = append(s.decls, namespaceDecl{prefix: prefix, uri: uri})
}

func (s *namespaceStack) pop(prefix, uri string) {
	for i := len(s.decls) - 1; i >= 0; i-- {
		if s.decls[i].uri == uri && (prefix == "" || s.decls[i].prefix == prefix) {
			s.decls = append(s.decls[:i], s.decls[i+1:]...)
			return
		}
	}
}

func (s *namespaceStack) prefixFor(uri string) string {
	if uri == "" {
		return ""
	}
	if uri == "http://schemas.android.com/apk/res/android" {
		return "android"
	}
	for i := len(s.decls) - 1; i >= 0; i-- {
		if s.decls[i].uri == uri {
			return s.decls[i].prefix
		}
	}
	if p, ok := s.generated[uri]; ok {
		return p
	}
	p := fmt.Sprintf("ns%d", len(s.generated)+1)
	s.generated[uri] = p
	return p
}

func (s *namespaceStack) pendingDeclarations() []namespaceDecl {
	out := make([]namespaceDecl, 0)
	for _, d := range s.decls {
		if !d.written {
			out = append(out, d)
		}
	}
	return out
}

func (s *namespaceStack) markDeclarationsWritten() {
	for i := range s.decls {
		s.decls[i].written = true
	}
}

func qualifyName(uri, name string, ns *namespaceStack) string {
	name = sanitizeName(name, "node")
	prefix := ns.prefixFor(uri)
	if prefix == "" {
		return name
	}
	return sanitizeName(prefix, "ns") + ":" + name
}

func sanitizeName(name, fallback string) string {
	name = strings.TrimSpace(name)
	if validXMLName(name) {
		return name
	}
	var b strings.Builder
	for i, r := range name {
		ok := r == '_' || r == '-' || r == '.' || r == ':' || r >= '0' && r <= '9' || r >= 'A' && r <= 'Z' || r >= 'a' && r <= 'z'
		if i == 0 && (r == '-' || r == '.' || r >= '0' && r <= '9') {
			ok = false
		}
		if ok {
			b.WriteRune(r)
		} else {
			b.WriteByte('_')
		}
	}
	if b.Len() == 0 || !validXMLName(b.String()) {
		return fallback
	}
	return b.String()
}

func writeIndent(out *bytes.Buffer, depth int) {
	for i := 0; i < depth; i++ {
		out.WriteString("    ")
	}
}

func escapeXML(s string) string {
	r := strings.NewReplacer(
		"&", "&amp;",
		"<", "&lt;",
		">", "&gt;",
		"\"", "&quot;",
		"'", "&apos;",
	)
	return r.Replace(s)
}
