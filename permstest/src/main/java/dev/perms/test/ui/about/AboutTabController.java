package dev.perms.test.ui.about;

import android.text.method.LinkMovementMethod;

import androidx.appcompat.app.AppCompatActivity;

import dev.perms.test.R;
import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.update.PermsTestUpdateController;

/** Owns About-tab formatting, help, and update-check binding. */
public final class AboutTabController {
    public interface Host {
        void debug(String area, String stage, String message);
        void warn(String area, String stage, String message);
        void appendOutput(String text);
    }

    private final AppCompatActivity activity;
    private final ActivityMainBinding binding;
    private final PermsTestUpdateController updateController;
    private final Host host;

    public AboutTabController(AppCompatActivity activity,
                              ActivityMainBinding binding,
                              PermsTestUpdateController updateController,
                              Host host) {
        this.activity = activity;
        this.binding = binding;
        this.updateController = updateController;
        this.host = host;
    }

    public void setup() {
        try {
            if (host != null) {
                host.debug("ui", "about", "binding About tab; version=" + activity.getString(R.string.about_version));
            }
            binding.tabAbout.txtAboutLinks.setMovementMethod(LinkMovementMethod.getInstance());
            AboutTabFormatter.apply(binding.tabAbout.txtAboutSummary, binding.tabAbout.txtAboutInfo, binding.tabAbout.txtAboutLinks);
            binding.tabAbout.btnAboutApiPermsTest.setOnClickListener(v -> showApiDialog(AboutApiDocsDialog.Section.PERMSTEST));
            binding.tabAbout.btnAboutApiAndroid.setOnClickListener(v -> showApiDialog(AboutApiDocsDialog.Section.ANDROID));
            binding.tabAbout.btnAboutApiBackends.setOnClickListener(v -> showApiDialog(AboutApiDocsDialog.Section.BACKENDS));
            binding.tabAbout.btnAboutApiPackages.setOnClickListener(v -> showApiDialog(AboutApiDocsDialog.Section.PACKAGES));
            binding.tabAbout.btnAboutApiPlugins.setOnClickListener(v -> showApiDialog(AboutApiDocsDialog.Section.PLUGINS));
            binding.tabAbout.btnOpenHelp.setOnClickListener(v -> showHelpDialog());
            if (updateController != null) updateController.bindAbout();
        } catch (Throwable t) {
            if (host != null) {
                host.warn("ui", "about", "setup failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
    }

    private void showHelpDialog() {
        if (host != null) host.debug("ui", "about-help", "open requested");
        AboutHelpDialog.show(activity, t -> {
            if (host != null) {
                host.warn("ui", "about-help", "show failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
                host.appendOutput("[!] Help dialog failed: " + t + "\n");
            }
        });
    }

    private void showApiDialog(AboutApiDocsDialog.Section section) {
        if (host != null) {
            String name = section == null ? "unknown" : section.name().toLowerCase(java.util.Locale.US);
            host.debug("ui", "about-api", "open requested: " + name);
        }
        AboutApiDocsDialog.show(activity, section, t -> {
            if (host != null) {
                host.warn("ui", "about-api", "show failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
                host.appendOutput("[!] API documentation dialog failed: " + t + "\n");
            }
        });
    }
}
