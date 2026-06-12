package org.github.tess1o.geopulse.maintenance;

import io.quarkus.arc.Arc;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import org.github.tess1o.geopulse.admin.service.AdminPasswordRecoveryResult;
import org.github.tess1o.geopulse.admin.service.AdminPasswordRecoveryService;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@QuarkusMain
public class GeoPulseMain implements QuarkusApplication {

    static final int EXIT_OK = 0;
    static final int EXIT_USAGE = 2;
    static final int EXIT_FAILED = 1;

    @Override
    public int run(String... args) {
        List<String> commandArgs = applicationArgs(args);
        if (commandArgs.isEmpty()) {
            Quarkus.waitForExit();
            return EXIT_OK;
        }

        if (commandArgs.size() >= 2
                && "admin".equals(commandArgs.get(0))
                && "reset-password".equals(commandArgs.get(1))) {
            return resetAdminPassword(commandArgs.subList(2, commandArgs.size()), System.out, System.err);
        }

        usage(System.err);
        return EXIT_USAGE;
    }

    int resetAdminPassword(List<String> args, PrintStream out, PrintStream err) {
        ResetPasswordCommand command;
        try {
            command = ResetPasswordCommand.parse(args);
        } catch (IllegalArgumentException exception) {
            err.println("Error: " + exception.getMessage());
            resetPasswordUsage(err);
            return EXIT_USAGE;
        }

        try {
            AdminPasswordRecoveryService service = Arc.container()
                    .instance(AdminPasswordRecoveryService.class)
                    .get();
            AdminPasswordRecoveryResult result = service.resetPassword(
                    command.email(),
                    command.password(),
                    command.promote(),
                    command.activate()
            );

            out.println("Password reset completed for " + result.email() + " (" + result.userId() + ").");
            if (result.promoted()) {
                out.println("User was promoted to ADMIN.");
            }
            if (result.activated()) {
                out.println("User account was activated.");
            }
            if (result.generatedPassword()) {
                out.println("Temporary password: " + result.temporaryPassword());
                out.println("Change this password after logging in.");
            }
            return EXIT_OK;
        } catch (Exception exception) {
            err.println("Error: " + exception.getMessage());
            return EXIT_FAILED;
        }
    }

    static List<String> applicationArgs(String... args) {
        List<String> filtered = new ArrayList<>();
        for (String arg : args) {
            if (arg.startsWith("-D") || arg.startsWith("-XX:")) {
                continue;
            }
            filtered.add(arg);
        }
        return filtered;
    }

    private static void usage(PrintStream stream) {
        stream.println("Usage:");
        resetPasswordUsage(stream);
    }

    private static void resetPasswordUsage(PrintStream stream) {
        stream.println("  geopulse admin reset-password --email <email> [--password <password>] [--promote] [--no-activate]");
    }

    record ResetPasswordCommand(String email, Optional<String> password, boolean promote, boolean activate) {

        static ResetPasswordCommand parse(List<String> args) {
            String email = null;
            String password = null;
            boolean promote = false;
            boolean activate = true;

            for (int i = 0; i < args.size(); i++) {
                String arg = args.get(i);
                switch (arg) {
                    case "--email" -> email = nextValue(args, ++i, "--email");
                    case "--password" -> password = nextValue(args, ++i, "--password");
                    case "--promote" -> promote = true;
                    case "--no-activate" -> activate = false;
                    case "--help", "-h" -> throw new IllegalArgumentException("Help requested");
                    default -> throw new IllegalArgumentException("Unknown option: " + arg);
                }
            }

            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("--email is required");
            }

            return new ResetPasswordCommand(email, Optional.ofNullable(password), promote, activate);
        }

        private static String nextValue(List<String> args, int index, String option) {
            if (index >= args.size() || args.get(index).startsWith("--")) {
                throw new IllegalArgumentException(option + " requires a value");
            }
            return args.get(index);
        }
    }
}
