// SPDX-License-Identifier: GPL-3.0-only
package io.github.gplaider.keliboard.imesmoke;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A disposable editor, so the smoke test never types into a user's app. */
public final class EditorActivity extends Activity {
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        // Leave room for system bars, including Android 15+ edge-to-edge enforcement.
        layout.setPadding(24, 120, 24, 24);
        TextView title = new TextView(this);
        title.setText("KeliBoard IME smoke editor");
        layout.addView(title);
        EditText editor = new EditText(this);
        editor.setId(R.id.smoke_editor);
        editor.setSingleLine(true);
        editor.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setImeOptions(EditorInfo.IME_FLAG_FORCE_ASCII
                | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        layout.addView(editor, new LinearLayout.LayoutParams(-1, -2));
        setContentView(layout);
        editor.requestFocus();
    }
}
