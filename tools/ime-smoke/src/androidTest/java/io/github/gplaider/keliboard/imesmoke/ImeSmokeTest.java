// SPDX-License-Identifier: GPL-3.0-only
package io.github.gplaider.keliboard.imesmoke;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(AndroidJUnit4.class)
public final class ImeSmokeTest {
    private static final long TIMEOUT = 15000;
    private static final String FIXTURE = "io.github.gplaider.keliboard.imesmoke";
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

    private void step(String message) {
        Log.i("ImeSmoke", message);
        Bundle status = new Bundle();
        status.putString("stream", "[IME SMOKE] " + message + "\n");
        InstrumentationRegistry.getInstrumentation().sendStatus(0, status);
    }

    private void evidence(String name) throws Exception {
        String run = InstrumentationRegistry.getArguments().getString("evidenceRun");
        assertTrue("Pass a unique -e evidenceRun <32 hex characters>",
                run != null && run.matches("[0-9a-f]{32}"));
        File dir = new File(context.getExternalFilesDir(null), "evidence/" + run);
        assertTrue("Cannot create evidence directory", dir.isDirectory() || dir.mkdirs());
        device.dumpWindowHierarchy(new File(dir, name + ".xml"));
        assertTrue("Screenshot failed", device.takeScreenshot(new File(dir, name + ".png")));
    }

    @Test
    public void keyboardInputAndSettings() throws Exception {
        String target = InstrumentationRegistry.getArguments().getString("targetPackage");
        assertNotNull("Pass -e targetPackage <keyboard applicationId>", target);
        String ime = target + "/helium314.keyboard.latin.LatinIME";
        try {
            step("Verify enabled and selected IME: " + ime);
            InputMethodManager imm = context.getSystemService(InputMethodManager.class);
            boolean enabled = false;
            for (InputMethodInfo info : imm.getEnabledInputMethodList()) {
                if (info.getId().equals(ime)) enabled = true;
            }
            assertTrue("KeliBoard IME is not enabled", enabled);
            assertEquals("KeliBoard is not the selected IME", ime,
                    Settings.Secure.getString(context.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD));

            // UI Automator's accessibility connection is active before the keyboard is created.
            // This exposes KeliBoard's virtual key nodes without enabling touch exploration.
            step("Open fixture and focus its empty editor");
            Intent editorIntent = new Intent(context, EditorActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(editorIntent);
            UiObject2 editor = device.wait(Until.findObject(By.res(FIXTURE, "smoke_editor")), TIMEOUT);
            assertNotNull("Fixture editor did not appear (is the device unlocked?)", editor);
            String initialText = editor.getText(); // empty accessibility text can be null
            assertTrue("Fixture editor must start empty", initialText == null || initialText.isEmpty());
            editor.click();
            assertTrue("Editor did not gain focus", device.wait(
                    Until.hasObject(By.res(FIXTURE, "smoke_editor").focused(true)), TIMEOUT));

            step("Tap the actual KeliBoard q, w, e keys");
            for (String letter : new String[]{"q", "w", "e"}) {
                UiObject2 key = device.wait(Until.findObject(By.pkg(target).desc(letter)), TIMEOUT);
                assertNotNull("Visible KeliBoard key missing: " + letter, key);
                key.click(); // injects a touch at the virtual key's screen bounds; no text/keyevent injection
            }
            assertTrue("Editor text did not become exactly qwe", device.wait(
                    Until.hasObject(By.res(FIXTURE, "smoke_editor").text("qwe")), TIMEOUT));
            evidence("01-keyboard-input");
            step("PASS: real keyboard touch input produced exactly qwe");

            step("Launch exported SettingsActivity and verify main settings content");
            context.startActivity(new Intent().setComponent(new ComponentName(target,
                    "helium314.keyboard.settings.SettingsActivity"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            Resources resources = context.getPackageManager().getResourcesForApplication(target);
            int titleId = resources.getIdentifier("language_and_layouts_title", "string", target);
            assertTrue("Cannot resolve main settings label", titleId != 0);
            assertTrue("Main settings content did not appear", device.wait(
                    Until.hasObject(By.pkg(target).text(resources.getString(titleId))), TIMEOUT));
            evidence("02-settings");
            step("PASS: main settings screen is visible");
        } catch (Throwable failure) {
            step("FAIL: " + failure);
            try {
                evidence("99-failure");
            } catch (Exception captureFailure) {
                Log.e("ImeSmoke", "Could not capture failure evidence", captureFailure);
            }
            throw failure;
        }
    }
}
