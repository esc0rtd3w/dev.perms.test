package dev.perms.test.tools.calc;

import android.app.Activity;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import java.math.BigInteger;
import java.util.Locale;

import dev.perms.test.R;
import dev.perms.test.ui.DropdownUi;
import dev.perms.test.ui.NoFilterArrayAdapter;

/** Reusable programmer calculator for hex/decimal/octal/binary conversion. */
public final class ToolsCalculatorController {
    public interface Host {
        Activity getActivity();
        View getRootView();
        void appendOutput(String message);
    }

    private static final String[] BASES = new String[] { "Hex", "Dec", "Oct", "Bin" };

    private final Host host;
    private boolean updating;

    public ToolsCalculatorController(Host host) {
        this.host = host;
    }

    public void bind() {
        Activity activity = activity();
        Views b = views();
        if (activity == null || b == null) return;

        b.ddToolsCalcBase.setAdapter(new NoFilterArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, BASES));
        b.ddToolsCalcBase.setText("Hex", false);
        DropdownUi.bindExposedDropdown(activity, b.tilToolsCalcBase, b.ddToolsCalcBase,
                () -> DropdownUi.showDropdown(b.ddToolsCalcBase));
        b.ddToolsCalcBase.setOnItemClickListener((parent, view, position, id) -> updateOutputs());

        b.edtToolsCalcInput.addTextChangedListener(new SimpleWatcher() {
            @Override public void afterTextChanged(Editable s) { updateOutputs(); }
        });

        bindAppend(b.btnToolsCalc0, "0");
        bindAppend(b.btnToolsCalc1, "1");
        bindAppend(b.btnToolsCalc2, "2");
        bindAppend(b.btnToolsCalc3, "3");
        bindAppend(b.btnToolsCalc4, "4");
        bindAppend(b.btnToolsCalc5, "5");
        bindAppend(b.btnToolsCalc6, "6");
        bindAppend(b.btnToolsCalc7, "7");
        bindAppend(b.btnToolsCalc8, "8");
        bindAppend(b.btnToolsCalc9, "9");
        bindAppend(b.btnToolsCalcA, "A");
        bindAppend(b.btnToolsCalcB, "B");
        bindAppend(b.btnToolsCalcC, "C");
        bindAppend(b.btnToolsCalcD, "D");
        bindAppend(b.btnToolsCalcE, "E");
        bindAppend(b.btnToolsCalcF, "F");
        bindAppend(b.btnToolsCalcPlus, " + ");
        bindAppend(b.btnToolsCalcMinus, " - ");
        bindAppend(b.btnToolsCalcMul, " * ");
        bindAppend(b.btnToolsCalcDiv, " / ");
        bindAppend(b.btnToolsCalcAnd, " & ");
        bindAppend(b.btnToolsCalcOr, " | ");
        bindAppend(b.btnToolsCalcXor, " ^ ");
        bindAppend(b.btnToolsCalcLsh, " << ");
        bindAppend(b.btnToolsCalcRsh, " >> ");
        bindAppend(b.btnToolsCalcNot, "~");
        bindAppend(b.btnToolsCalcParenLeft, "(");
        bindAppend(b.btnToolsCalcParenRight, ")");
        b.btnToolsCalcBackspace.setOnClickListener(v -> backspace());
        b.btnToolsCalcClear.setOnClickListener(v -> {
            b.edtToolsCalcInput.setText("");
            setStatus("Cleared.");
        });
        updateOutputs();
    }

    private void bindAppend(MaterialButton button, String text) {
        if (button == null) return;
        button.setOnClickListener(v -> appendInput(text));
    }

    private void appendInput(String value) {
        Views b = views();
        if (b == null || b.edtToolsCalcInput == null) return;
        int start = Math.max(0, b.edtToolsCalcInput.getSelectionStart());
        int end = Math.max(0, b.edtToolsCalcInput.getSelectionEnd());
        if (end < start) {
            int tmp = start;
            start = end;
            end = tmp;
        }
        Editable editable = b.edtToolsCalcInput.getText();
        if (editable == null) {
            b.edtToolsCalcInput.setText(value);
            b.edtToolsCalcInput.setSelection(value.length());
        } else {
            editable.replace(start, end, value);
        }
    }

    private void backspace() {
        Views b = views();
        if (b == null || b.edtToolsCalcInput == null) return;
        Editable editable = b.edtToolsCalcInput.getText();
        if (editable == null || editable.length() == 0) return;
        int start = Math.max(0, b.edtToolsCalcInput.getSelectionStart());
        int end = Math.max(0, b.edtToolsCalcInput.getSelectionEnd());
        if (end < start) {
            int tmp = start;
            start = end;
            end = tmp;
        }
        if (start != end) editable.delete(start, end);
        else if (start > 0) editable.delete(start - 1, start);
    }

    private void updateOutputs() {
        if (updating) return;
        Views b = views();
        if (b == null) return;
        String expression = b.edtToolsCalcInput.getText() == null ? "" : b.edtToolsCalcInput.getText().toString().trim();
        if (TextUtils.isEmpty(expression)) {
            setResultText(b.txtToolsCalcHex, "Hex: 0");
            setResultText(b.txtToolsCalcDec, "Dec: 0");
            setResultText(b.txtToolsCalcOct, "Oct: 0");
            setResultText(b.txtToolsCalcBin, "Bin: 0");
            setStatus("Enter a value or expression. Results update as you type.");
            return;
        }
        try {
            BigInteger value = new Parser(expression, selectedBase()).parse();
            setResultText(b.txtToolsCalcHex, "Hex: " + value.toString(16).toUpperCase(Locale.US));
            setResultText(b.txtToolsCalcDec, "Dec: " + value.toString(10));
            setResultText(b.txtToolsCalcOct, "Oct: " + value.toString(8));
            setResultText(b.txtToolsCalcBin, "Bin: " + value.toString(2));
            setStatus("OK");
        } catch (Throwable t) {
            setStatus("Waiting for a valid expression: " + safeMessage(t));
        }
    }

    private int selectedBase() {
        Views b = views();
        String text = b == null || b.ddToolsCalcBase.getText() == null ? "Hex" : b.ddToolsCalcBase.getText().toString();
        if ("Dec".equalsIgnoreCase(text)) return 10;
        if ("Oct".equalsIgnoreCase(text)) return 8;
        if ("Bin".equalsIgnoreCase(text)) return 2;
        return 16;
    }

    private static void setResultText(TextView view, String text) {
        if (view != null) view.setText(text == null ? "" : text);
    }

    private void setStatus(String message) {
        Views b = views();
        if (b != null && b.txtToolsCalcStatus != null) b.txtToolsCalcStatus.setText(message == null ? "" : message);
    }


    private Activity activity() {
        return host == null ? null : host.getActivity();
    }

    private Views views() {
        View root = host == null ? null : host.getRootView();
        return root == null ? null : new Views(root);
    }

    private static final class Views {
        final com.google.android.material.textfield.TextInputEditText edtToolsCalcInput;
        final com.google.android.material.textfield.TextInputLayout tilToolsCalcBase;
        final com.google.android.material.textfield.MaterialAutoCompleteTextView ddToolsCalcBase;
        final MaterialButton btnToolsCalcA;
        final MaterialButton btnToolsCalcB;
        final MaterialButton btnToolsCalcC;
        final MaterialButton btnToolsCalcD;
        final MaterialButton btnToolsCalcE;
        final MaterialButton btnToolsCalcF;
        final MaterialButton btnToolsCalc0;
        final MaterialButton btnToolsCalc1;
        final MaterialButton btnToolsCalc2;
        final MaterialButton btnToolsCalc3;
        final MaterialButton btnToolsCalc4;
        final MaterialButton btnToolsCalc5;
        final MaterialButton btnToolsCalc6;
        final MaterialButton btnToolsCalc7;
        final MaterialButton btnToolsCalc8;
        final MaterialButton btnToolsCalc9;
        final MaterialButton btnToolsCalcDiv;
        final MaterialButton btnToolsCalcMul;
        final MaterialButton btnToolsCalcMinus;
        final MaterialButton btnToolsCalcPlus;
        final MaterialButton btnToolsCalcAnd;
        final MaterialButton btnToolsCalcOr;
        final MaterialButton btnToolsCalcXor;
        final MaterialButton btnToolsCalcNot;
        final MaterialButton btnToolsCalcLsh;
        final MaterialButton btnToolsCalcRsh;
        final MaterialButton btnToolsCalcParenLeft;
        final MaterialButton btnToolsCalcParenRight;
        final MaterialButton btnToolsCalcBackspace;
        final MaterialButton btnToolsCalcClear;
        final TextView txtToolsCalcStatus;
        final TextView txtToolsCalcHex;
        final TextView txtToolsCalcDec;
        final TextView txtToolsCalcOct;
        final TextView txtToolsCalcBin;

        Views(View root) {
            edtToolsCalcInput = root.findViewById(R.id.edtToolsCalcInput);
            tilToolsCalcBase = root.findViewById(R.id.tilToolsCalcBase);
            ddToolsCalcBase = root.findViewById(R.id.ddToolsCalcBase);
            btnToolsCalcA = root.findViewById(R.id.btnToolsCalcA);
            btnToolsCalcB = root.findViewById(R.id.btnToolsCalcB);
            btnToolsCalcC = root.findViewById(R.id.btnToolsCalcC);
            btnToolsCalcD = root.findViewById(R.id.btnToolsCalcD);
            btnToolsCalcE = root.findViewById(R.id.btnToolsCalcE);
            btnToolsCalcF = root.findViewById(R.id.btnToolsCalcF);
            btnToolsCalc0 = root.findViewById(R.id.btnToolsCalc0);
            btnToolsCalc1 = root.findViewById(R.id.btnToolsCalc1);
            btnToolsCalc2 = root.findViewById(R.id.btnToolsCalc2);
            btnToolsCalc3 = root.findViewById(R.id.btnToolsCalc3);
            btnToolsCalc4 = root.findViewById(R.id.btnToolsCalc4);
            btnToolsCalc5 = root.findViewById(R.id.btnToolsCalc5);
            btnToolsCalc6 = root.findViewById(R.id.btnToolsCalc6);
            btnToolsCalc7 = root.findViewById(R.id.btnToolsCalc7);
            btnToolsCalc8 = root.findViewById(R.id.btnToolsCalc8);
            btnToolsCalc9 = root.findViewById(R.id.btnToolsCalc9);
            btnToolsCalcDiv = root.findViewById(R.id.btnToolsCalcDiv);
            btnToolsCalcMul = root.findViewById(R.id.btnToolsCalcMul);
            btnToolsCalcMinus = root.findViewById(R.id.btnToolsCalcMinus);
            btnToolsCalcPlus = root.findViewById(R.id.btnToolsCalcPlus);
            btnToolsCalcAnd = root.findViewById(R.id.btnToolsCalcAnd);
            btnToolsCalcOr = root.findViewById(R.id.btnToolsCalcOr);
            btnToolsCalcXor = root.findViewById(R.id.btnToolsCalcXor);
            btnToolsCalcNot = root.findViewById(R.id.btnToolsCalcNot);
            btnToolsCalcLsh = root.findViewById(R.id.btnToolsCalcLsh);
            btnToolsCalcRsh = root.findViewById(R.id.btnToolsCalcRsh);
            btnToolsCalcParenLeft = root.findViewById(R.id.btnToolsCalcParenLeft);
            btnToolsCalcParenRight = root.findViewById(R.id.btnToolsCalcParenRight);
            btnToolsCalcBackspace = root.findViewById(R.id.btnToolsCalcBackspace);
            btnToolsCalcClear = root.findViewById(R.id.btnToolsCalcClear);
            txtToolsCalcStatus = root.findViewById(R.id.txtToolsCalcStatus);
            txtToolsCalcHex = root.findViewById(R.id.txtToolsCalcHex);
            txtToolsCalcDec = root.findViewById(R.id.txtToolsCalcDec);
            txtToolsCalcOct = root.findViewById(R.id.txtToolsCalcOct);
            txtToolsCalcBin = root.findViewById(R.id.txtToolsCalcBin);
        }
    }

    private static String safeMessage(Throwable t) {
        String msg = t == null ? null : t.getMessage();
        return TextUtils.isEmpty(msg) ? String.valueOf(t) : msg;
    }

    private abstract static class SimpleWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }

    private static final class Parser {
        private final String s;
        private final int defaultBase;
        private int pos;

        Parser(String s, int defaultBase) {
            this.s = s == null ? "" : s;
            this.defaultBase = defaultBase;
        }

        BigInteger parse() {
            BigInteger value = parseOr();
            skipWs();
            if (pos < s.length()) throw new IllegalArgumentException("Unexpected: " + s.charAt(pos));
            return value;
        }

        private BigInteger parseOr() {
            BigInteger value = parseXor();
            while (true) {
                skipWs();
                if (match('|')) value = value.or(parseXor());
                else return value;
            }
        }

        private BigInteger parseXor() {
            BigInteger value = parseAnd();
            while (true) {
                skipWs();
                if (match('^')) value = value.xor(parseAnd());
                else return value;
            }
        }

        private BigInteger parseAnd() {
            BigInteger value = parseShift();
            while (true) {
                skipWs();
                if (match('&')) value = value.and(parseShift());
                else return value;
            }
        }

        private BigInteger parseShift() {
            BigInteger value = parseAdd();
            while (true) {
                skipWs();
                if (match("<<")) value = value.shiftLeft(parseAdd().intValue());
                else if (match(">>")) value = value.shiftRight(parseAdd().intValue());
                else return value;
            }
        }

        private BigInteger parseAdd() {
            BigInteger value = parseMul();
            while (true) {
                skipWs();
                if (match('+')) value = value.add(parseMul());
                else if (match('-')) value = value.subtract(parseMul());
                else return value;
            }
        }

        private BigInteger parseMul() {
            BigInteger value = parseUnary();
            while (true) {
                skipWs();
                if (match('*')) value = value.multiply(parseUnary());
                else if (match('/')) value = value.divide(parseUnary());
                else if (match('%')) value = value.remainder(parseUnary());
                else return value;
            }
        }

        private BigInteger parseUnary() {
            skipWs();
            if (match('+')) return parseUnary();
            if (match('-')) return parseUnary().negate();
            if (match('~')) return parseUnary().not();
            return parsePrimary();
        }

        private BigInteger parsePrimary() {
            skipWs();
            if (match('(')) {
                BigInteger value = parseOr();
                skipWs();
                if (!match(')')) throw new IllegalArgumentException("Missing )");
                return value;
            }
            return parseNumber();
        }

        private BigInteger parseNumber() {
            skipWs();
            int start = pos;
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (Character.isLetterOrDigit(c) || c == 'x' || c == 'X') pos++;
                else break;
            }
            if (start == pos) throw new IllegalArgumentException("Expected number");
            String token = s.substring(start, pos).replace("_", "");
            int base = defaultBase;
            if (token.startsWith("0x") || token.startsWith("0X")) {
                base = 16;
                token = token.substring(2);
            } else if (token.startsWith("0b") || token.startsWith("0B")) {
                base = 2;
                token = token.substring(2);
            } else if (token.startsWith("0o") || token.startsWith("0O")) {
                base = 8;
                token = token.substring(2);
            }
            if (TextUtils.isEmpty(token)) throw new IllegalArgumentException("Empty number");
            return new BigInteger(token, base);
        }

        private void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        private boolean match(char c) {
            skipWs();
            if (pos < s.length() && s.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        private boolean match(String text) {
            skipWs();
            if (s.startsWith(text, pos)) {
                pos += text.length();
                return true;
            }
            return false;
        }
    }
}
