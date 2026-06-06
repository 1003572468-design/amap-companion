package com.autonavi.companion;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.BulletSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

final class MarkdownRenderer {
    private MarkdownRenderer() {
    }

    static CharSequence render(Context context, String markdown) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        if (TextUtils.isEmpty(markdown)) {
            return out;
        }
        boolean codeBlock = false;
        String[] lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        for (String line : lines) {
            if (line.trim().startsWith("```")) {
                codeBlock = !codeBlock;
                continue;
            }
            int start = out.length();
            if (codeBlock) {
                out.append(line).append('\n');
                applyCodeStyle(out, start, out.length());
                continue;
            }
            if (line.startsWith("## ")) {
                appendInlineMarkdown(out, line.substring(3));
                int end = out.length();
                out.append('\n');
                out.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new RelativeSizeSpan(1.18f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (line.startsWith("# ")) {
                appendInlineMarkdown(out, line.substring(2));
                int end = out.length();
                out.append('\n');
                out.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new RelativeSizeSpan(1.28f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (line.startsWith("- ")) {
                appendInlineMarkdown(out, line.substring(2));
                int end = out.length();
                out.append('\n');
                out.setSpan(new BulletSpan(dp(context, 10)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                appendInlineMarkdown(out, line);
                out.append('\n');
            }
        }
        return out;
    }

    private static void appendInlineMarkdown(SpannableStringBuilder out, String text) {
        if (TextUtils.isEmpty(text)) {
            return;
        }
        int index = 0;
        while (index < text.length()) {
            int open = text.indexOf('`', index);
            if (open < 0) {
                out.append(text.substring(index));
                return;
            }
            out.append(text.substring(index, open));
            int close = text.indexOf('`', open + 1);
            if (close < 0) {
                out.append(text.substring(open));
                return;
            }
            int start = out.length();
            out.append(text.substring(open + 1, close));
            int end = out.length();
            applyCodeStyle(out, start, end);
            index = close + 1;
        }
    }

    private static void applyCodeStyle(SpannableStringBuilder out, int start, int end) {
        out.setSpan(new TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new BackgroundColorSpan(0xFFE5E7EB), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
