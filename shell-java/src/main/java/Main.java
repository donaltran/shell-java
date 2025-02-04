import java.util.Arrays;
import java.util.Scanner;
import java.io.*;
import java.util.*;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static final String[] BUILTINS = {"echo", "exit", "type", "pwd", "cd"};
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        String input;
        
        while (true) {
            System.out.print("$ ");
            input = readInputWithAutocomplete(scanner);
            input = input.trim();
            
            String[] tokens = parseArguments(input);
            if (tokens.length == 0) {
                continue;
            }

            String command = tokens[0];
            String[] cmdArgs = Arrays.copyOfRange(tokens, 1, tokens.length);

            if (command.equals("exit")) {
                int exitCode = 0;
                if (cmdArgs.length > 0) {
                    try {
                        exitCode = Integer.parseInt(cmdArgs[0]);
                    } catch (NumberFormatException e) {
                        System.out.println("exit: " + cmdArgs[0] + ": numeric argument required");
                        exitCode = 2;
                    }
                }
                System.exit(exitCode);
            } else if (command.equals("echo")) {
                handleEchoCommand(cmdArgs);
            } else if (command.equals("pwd")) {
                System.out.println(System.getProperty("user.dir"));
            } else if (command.equals("cd")) {
                handleCdCommand(cmdArgs);
            } else if (command.equals("type")) {
                handleTypeCommand(cmdArgs, BUILTINS);
            } else {
                handleExternalCommand(tokens);
            }
        }
    }

    private static void handleEchoCommand(String[] cmdArgs) {
        int redirectionIndex = -1;
        String redirectionOperator = null;
        for (int i = 0; i < cmdArgs.length; i++) {
            String arg = cmdArgs[i];
            if (arg.equals(">") || arg.equals("1>") || arg.equals("2>") 
                    || arg.equals(">>") || arg.equals("1>>") || arg.equals("2>>")) {
                redirectionIndex = i;
                redirectionOperator = arg;
                break;
            }
        }
        
        if (redirectionIndex != -1) {
            String outputFile = cmdArgs[redirectionIndex + 1];
            String[] echoArgs = Arrays.copyOfRange(cmdArgs, 0, redirectionIndex);
            String echoOutput = String.join(" ", echoArgs) + "\n";

            try {
                File file = new File(outputFile);
                File parentDir = file.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }

                if (redirectionOperator.equals("2>") || redirectionOperator.equals("2>>")) {
                    boolean append = redirectionOperator.endsWith(">>");
                    try (FileWriter fw = new FileWriter(file, append)) { }
                    // Echos output is still printed to stdout
                    System.out.println(echoOutput.trim());
                } else {
                    boolean append = redirectionOperator.endsWith(">>");
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, append))) {
                        writer.write(echoOutput);
                    }
                }
            } catch (IOException e) {
                System.out.println("Error writing to file: " + e.getMessage());
            }
        } else {
            System.out.println(String.join(" ", cmdArgs));
        }
    }

    private static void handleExternalCommand(String[] tokens) {
        int redirectionIndex = -1;
        String redirectionOperator = null;
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            if (token.equals(">") || token.equals("1>") || token.equals("2>") 
                    || token.equals(">>") || token.equals("1>>") || token.equals("2>>")) {
                redirectionIndex = i;
                redirectionOperator = token;
                break;
            }
        }

        if (redirectionIndex != -1) {
            String outputFile = tokens[redirectionIndex + 1];
            String[] commandTokens = Arrays.copyOfRange(tokens, 0, redirectionIndex);

            try {
                File file = new File(outputFile);
                File parentDir = file.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }

                ProcessBuilder processBuilder = new ProcessBuilder(commandTokens);
                processBuilder.directory(new File(System.getProperty("user.dir")));

                if (redirectionOperator.startsWith("2")) {
                    boolean append = redirectionOperator.endsWith(">>");
                    if (append) {
                        processBuilder.redirectError(ProcessBuilder.Redirect.appendTo(file));
                    } else {
                        processBuilder.redirectError(ProcessBuilder.Redirect.to(file));
                    }
                    processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                } else {
                    boolean append = redirectionOperator.endsWith(">>");
                    if (append) {
                        processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(file));
                    } else {
                        processBuilder.redirectOutput(ProcessBuilder.Redirect.to(file));
                    }
                    processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
                }

            Process process = processBuilder.start();
            process.waitFor();
        
            } catch (IOException | InterruptedException e) {
                System.out.println("Error executing command: " + e.getMessage());
            }
        } else {
            try {
                ProcessBuilder processBuilder = new ProcessBuilder(tokens);
                processBuilder.redirectErrorStream(true);
                processBuilder.directory(new File(System.getProperty("user.dir")));
                Process process = processBuilder.start();
                    
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
                    
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    // If needed, handle non-zero exit codes here
                }
            } catch (IOException e) {
                System.out.println(tokens[0] + ": not found");
            } catch (InterruptedException e) {
                System.out.println("Command interrupted: " + e.getMessage());
            }
        }
    }
    

    private static void handleCdCommand(String[] cmdArgs) {
        String targetDir = cmdArgs.length > 0 ? cmdArgs[0] : System.getenv("HOME");
        if (targetDir == null) {
            System.out.println("cd: HOME not set");
            return;
        }

        targetDir = targetDir.replace("~", System.getenv("HOME"));
        Path targetPath = Paths.get(targetDir).normalize();
        
        if (!targetPath.isAbsolute()) {
            targetPath = Paths.get(System.getProperty("user.dir")).resolve(targetDir).normalize();
        }

        if (Files.isDirectory(targetPath)) {
            System.setProperty("user.dir", targetPath.toString());
        } else {
            System.out.println("cd: " + targetDir + ": No such file or directory");
        }
    }

    private static void handleTypeCommand(String[] cmdArgs, String[] builtins) {
        if (cmdArgs.length == 0) {
            System.out.println("type: missing operand");
            return;
        }

        String typeTarget = cmdArgs[0];
        if (Arrays.asList(builtins).contains(typeTarget)) {
            System.out.println(typeTarget + " is a shell builtin");
            return;
        }

        String pathEnv = System.getenv("PATH");
        List<String> directories = Arrays.asList(pathEnv.split(":"));
        for (String dir : directories) {
            Path filePath = Paths.get(dir, typeTarget);
            if (Files.exists(filePath) && Files.isExecutable(filePath)) {
                System.out.println(typeTarget + " is " + filePath.toString());
                return;
            }
        }

        System.out.println(typeTarget + ": not found");
    }

private static String[] parseArguments(String input) {
        List<String> args = new ArrayList<>();
        StringBuilder currentArg = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;

        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);

            if (inSingleQuotes) {
                if (ch == '\'') {
                    inSingleQuotes = false;
                } else {
                    currentArg.append(ch);
                }
            } else if (inDoubleQuotes) {
                if (ch == '\\') {
                    if (i + 1 < input.length()) {
                        char nextCh = input.charAt(i + 1);
                        if (nextCh == '\\' || nextCh == '$' || nextCh == '"') {
                            currentArg.append(nextCh);
                            i++; // Skip the next character
                        } else {
                            currentArg.append(ch); // Add the backslash as literal
                        }
                    } else {
                        currentArg.append(ch); // Backslash at end of input
                    }
                } else if (ch == '"') {
                    inDoubleQuotes = false;
                } else {
                    currentArg.append(ch);
                }
            } else {
                if (ch == '\\') {
                    // Escape next character outside quotes
                    if (i + 1 < input.length()) {
                        currentArg.append(input.charAt(i + 1));
                        i++; // Skip the next character
                    } else {
                        currentArg.append(ch); // Backslash at end
                    }
                } else if (ch == '\'') {
                    inSingleQuotes = true;
                } else if (ch == '"') {
                    inDoubleQuotes = true;
                } else if (ch == ' ') {
                    if (currentArg.length() > 0) {
                        args.add(currentArg.toString());
                        currentArg.setLength(0);
                    }
                } else {
                    currentArg.append(ch);
                }
            }
        }

        if (currentArg.length() > 0) {
            args.add(currentArg.toString());
        }

        return args.toArray(new String[0]);
    }


    private static String readInputWithAutocomplete(Scanner scanner) {
        StringBuilder input = new StringBuilder();
        while (true) {
            String line = scanner.nextLine();
            if (line.contains("\t")) { // Tab key pressed
                String partialCommand = input.toString().trim();
                String completedCommand = autocompleteCommand(partialCommand);
                if (completedCommand != null) {
                    input.setLength(0); // Clear the current input
                    input.append(completedCommand).append(" "); // Append completed command with space
    
                    // Clear the current line and rewrite the prompt
                    System.out.print("\033[2K"); // Clear the entire line
                    System.out.print("\r$ " + input.toString()); // Rewrite the prompt
                    System.out.flush(); // Ensure the output is flushed
                }
            } else {
                input.append(line);
                break;
            }
        }
        return input.toString();
    }


    private static String autocompleteCommand(String partialCommand) {
        for (String builtin : BUILTINS) {
            if (builtin.startsWith(partialCommand)) {
                return builtin; // Return the completed command
            }
        }
        return null; // No match found
    }
}