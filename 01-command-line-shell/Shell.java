import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.zip.*;

/*
Seif Wael Naem
Mohamed Kamal
Mohamed Waleed
Marwan Ibraheem
 */

class Main {

    public static void main(String[] args) {
        try (Scanner sc = new Scanner(System.in)) {
            Terminal terminal = new Terminal();
            Parser parser = new Parser();

            while (true) {
                System.out.print(terminal.getCurrentDir() + " $ ");
                String line;
                try {
                    line = sc.nextLine();
                } catch (NoSuchElementException e) {
                    break;
                }

                if (line.trim().isEmpty()) continue;

                ParsedCommand pc = parser.parse(line);
                if (pc == null) continue;

                if (pc.command.equals("exit")) break;

                try {
                    String output = terminal.execute(pc);
                    if (pc.redirectTo != null) {
                        terminal.redirectOutput(pc, output);
                    } else {
                        System.out.print(output);
                    }
                } catch (Exception ex) {
                    System.err.println("Error: " + ex.getMessage());
                }
            }

            System.out.println("Goodbye.");
        }
    }
}

class Terminal {
    private Path currentDir;

    public Terminal() {
        currentDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    public Path getCurrentDir() {
        return currentDir;
    }

    public void redirectOutput(ParsedCommand pc, String output) {
        Path target = resolvePath(pc.redirectTo);
        try {
            if (target.getParent() != null)
                Files.createDirectories(target.getParent());
            if (pc.appendRedirect) {
                Files.write(target, output.getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else {
                Files.write(target, output.getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
        } catch (IOException ioe) {
            System.err.println("Redirection failed: " + ioe.getMessage());
        }
    }

    private Path resolvePath(String p) {
        Path path = Paths.get(p);
        if (path.isAbsolute()) return path.normalize();
        return currentDir.resolve(path).normalize();
    }

    public String execute(ParsedCommand pc) throws Exception {
        String cmd = pc.command;
        List<String> args = pc.args;
        StringBuilder out = new StringBuilder();

        switch (cmd) {
            case "pwd":  out.append(currentDir).append(System.lineSeparator()); break;
            case "cd":   handleCd(args, out); break;
            case "ls":   handleLs(args, out); break;
            case "mkdir": handleMkdir(args, out); break;
            case "rmdir": handleRmdir(args, out); break;
            case "touch": handleTouch(args, out); break;
            case "cp":   handleCp(args, out); break;
            case "rm":   handleRm(args, out); break;
            case "cat":  handleCat(args, out); break;
            case "wc":   handleWc(args, out); break;
            case "zip":  handleZip(args, out); break;
            case "unzip": handleUnzip(args, out); break;
            case "echo": out.append(String.join(" ", args)).append(System.lineSeparator()); break;
            default: out.append("Unknown command: ").append(cmd).append(System.lineSeparator());
        }

        return out.toString();
    }

    private void handleCd(List<String> args, StringBuilder out) {
        try {
            if (args.isEmpty()) {
                currentDir = Paths.get(System.getProperty("user.home")).toAbsolutePath().normalize();
            } else if (args.size() == 1) {
                Path target = resolvePath(args.get(0));
                if (!Files.exists(target))
                    out.append("cd: no such directory").append(System.lineSeparator());
                else if (!Files.isDirectory(target))
                    out.append("cd: not a directory").append(System.lineSeparator());
                else
                    currentDir = target.toAbsolutePath().normalize();
            } else {
                out.append("cd: too many arguments").append(System.lineSeparator());
            }
        } catch (Exception e) {
            out.append("cd error: ").append(e.getMessage()).append(System.lineSeparator());
        }
    }

    private void handleLs(List<String> args, StringBuilder out) {
        Path target = args.isEmpty() ? currentDir : resolvePath(args.get(0));
        if (!Files.exists(target)) {
            out.append("ls: directory not found").append(System.lineSeparator());
            return;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(target)) {
            List<String> names = new ArrayList<>();
            for (Path p : ds) names.add(p.getFileName().toString());
            Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
            for (String n : names) out.append(n).append(System.lineSeparator());
        } catch (IOException e) {
            out.append("ls error: ").append(e.getMessage()).append(System.lineSeparator());
        }
    }

    private void handleMkdir(List<String> args, StringBuilder out) {
        if (args.isEmpty()) {
            out.append("mkdir: missing operand").append(System.lineSeparator());
            return;
        }
        for (String a : args) {
            try {
                Path p = resolvePath(a);
                if (Files.exists(p)) {
                    out.append("mkdir: cannot create directory '").append(a).append("': File exists").append(System.lineSeparator());
                } else {
                    Files.createDirectories(p);
                }
            } catch (Exception e) {
                out.append("mkdir error: ").append(e.getMessage()).append(System.lineSeparator());
            }
        }
    }

    private void handleRmdir(List<String> args, StringBuilder out) {
        if (args.size() != 1) {
            out.append("rmdir: requires one argument").append(System.lineSeparator());
            return;
        }
        String a = args.get(0);
        try {
            if (a.equals("*")) {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(currentDir)) {
                    for (Path p : ds) {
                        if (Files.isDirectory(p)) {
                            try (DirectoryStream<Path> inner = Files.newDirectoryStream(p)) {
                                if (!inner.iterator().hasNext()) Files.delete(p);
                            }
                        }
                    }
                }
            } else {
                Path dir = resolvePath(a);
                if (Files.isDirectory(dir)) {
                    try (DirectoryStream<Path> inner = Files.newDirectoryStream(dir)) {
                        if (!inner.iterator().hasNext()) Files.delete(dir);
                        else out.append("rmdir: directory not empty").append(System.lineSeparator());
                    }
                } else out.append("rmdir: not a directory").append(System.lineSeparator());
            }
        } catch (Exception e) {
            out.append("rmdir error: ").append(e.getMessage()).append(System.lineSeparator());
        }
    }

    private void handleTouch(List<String> args, StringBuilder out) {
        if (args.size() != 1) {
            out.append("touch: requires one file name").append(System.lineSeparator());
            return;
        }
        Path file = resolvePath(args.get(0));
        try {
            if (!Files.exists(file)) Files.createFile(file);
            else Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()));
        } catch (IOException e) {
            out.append("touch error: ").append(e.getMessage()).append(System.lineSeparator());
        }
    }

    private void handleCp(List<String> args, StringBuilder out) {
        if (args.size() < 2) {
            out.append("cp: requires source and destination").append(System.lineSeparator());
            return;
        }
        boolean recursive = args.get(0).equals("-r");
        Path src, dest;
        if (recursive) {
            if (args.size() != 3) {
                out.append("cp -r: requires 2 arguments").append(System.lineSeparator());
                return;
            }
            src = resolvePath(args.get(1));
            dest = resolvePath(args.get(2));
        } else {
            src = resolvePath(args.get(0));
            dest = resolvePath(args.get(1));
        }

        try {
            if (recursive) copyRecursive(src, dest);
            else Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            out.append("cp error: ").append(e.getMessage()).append(System.lineSeparator());
        }
    }

    private void copyRecursive(Path src, final Path dest) throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = dest.resolve(src.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path targetFile = dest.resolve(src.relativize(file));
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void handleRm(List<String> args, StringBuilder out) {
        if (args.size() != 1) {
            out.append("rm: requires one argument").append(System.lineSeparator());
            return;
        }
        Path p = resolvePath(args.get(0));
        try {
            if (Files.isRegularFile(p)) Files.delete(p);
            else out.append("rm: not a file").append(System.lineSeparator());
        } catch (IOException e) {
            out.append("rm error: ").append(e.getMessage()).append(System.lineSeparator());
        }
    }

    private void handleCat(List<String> args, StringBuilder out) {
        if (args.isEmpty()) {
            out.append("cat: missing argument").append(System.lineSeparator());
            return;
        }
        for (String a : args) {
            Path p = resolvePath(a);
            try {
                Files.lines(p).forEach(line -> out.append(line).append(System.lineSeparator()));
            } catch (IOException e) {
                out.append("cat error: ").append(e.getMessage()).append(System.lineSeparator());
            }
        }
    }

    private void handleWc(List<String> args, StringBuilder out) {
        if (args.size() != 1) {
            out.append("wc: requires file name").append(System.lineSeparator());
            return;
        }
        Path p = resolvePath(args.get(0));
        try {
            String content = Files.readString(p);
            long lines = content.split("\r\n|\r|\n").length;
            long words = Arrays.stream(content.split("\\s+")).filter(w -> !w.isEmpty()).count();
            long chars = content.length();
            out.append(lines).append(" ").append(words).append(" ").append(chars).append(" ").append(p.getFileName()).append(System.lineSeparator());
        } catch (IOException e) {
            out.append("wc error: ").append(e.getMessage()).append(System.lineSeparator());
        }
    }

    private void handleZip(List<String> args, StringBuilder out) {
        if (args.size() < 2) {
            out.append("zip: requires archive and file(s)").append(System.lineSeparator());
            return;
        }
        boolean recursive = args.get(0).equals("-r");
        int idx = recursive ? 1 : 0;
        Path zipFile = resolvePath(args.get(idx));
        List<String> items = args.subList(idx + 1, args.size());

        if (Files.exists(zipFile) && Files.isDirectory(zipFile)) {
            out.append("zip error: target archive '").append(zipFile).append("' is a directory").append(System.lineSeparator());
            return;
        }

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile.toFile()))) {
            for (String item : items) {
                Path path = resolvePath(item);
                if (!Files.exists(path)) {
                    out.append("zip: ").append(item).append(": No such file or directory").append(System.lineSeparator());
                    continue;
                }

                if (Files.isDirectory(path)) {
                    if (!recursive) {
                        out.append("zip: cannot zip directory '").append(item).append("' without -r").append(System.lineSeparator());
                        continue;
                    }

                    final Path base = path;
                    final String baseName = base.getFileName().toString();
                    Files.walkFileTree(base, new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                            Path rel = base.relativize(dir);
                            String entryName = rel.toString().isEmpty() ? baseName + "/" : baseName + "/" + rel.toString().replace('\\', '/');
                            zos.putNextEntry(new ZipEntry(entryName));
                            zos.closeEntry();
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            Path rel = base.relativize(file);
                            String entryName = baseName + "/" + rel.toString().replace('\\', '/');
                            zos.putNextEntry(new ZipEntry(entryName));
                            Files.copy(file, zos);
                            zos.closeEntry();
                            return FileVisitResult.CONTINUE;
                        }
                    });

                } else if (Files.isRegularFile(path)) {
                    String entryName = path.getFileName().toString();
                    zos.putNextEntry(new ZipEntry(entryName));
                    Files.copy(path, zos);
                    zos.closeEntry();
                }
            }
        } catch (IOException e) {
            out.append("zip error: ").append(e.getMessage()).append(System.lineSeparator());
        }
    }

    private void handleUnzip(List<String> args, StringBuilder out) {
        if (args.isEmpty()) {
            out.append("unzip: missing archive").append(System.lineSeparator());
            return;
        }
        Path zipFile = null;
        Path dest = currentDir;

        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if ("-d".equals(a)) {
                if (i + 1 < args.size()) {
                    dest = resolvePath(args.get(i + 1));
                    i++;
                } else {
                    out.append("unzip: missing destination after -d").append(System.lineSeparator());
                    return;
                }
            } else if (zipFile == null) {
                zipFile = resolvePath(a);
            } else {
            }
        }

        if (zipFile == null) {
            out.append("unzip: missing archive").append(System.lineSeparator());
            return;
        }

        try {
            if (Files.exists(dest) && !Files.isDirectory(dest)) {
                out.append("unzip error: destination exists and is not a directory").append(System.lineSeparator());
                return;
            }
            if (!Files.exists(dest)) Files.createDirectories(dest);

            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile.toFile()))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path outPath = dest.resolve(entry.getName()).normalize();
                    if (!outPath.startsWith(dest.toAbsolutePath())) {
                        out.append("unzip error: entry outside target dir: ").append(entry.getName()).append(System.lineSeparator());
                        zis.closeEntry();
                        continue;
                    }
                    if (entry.isDirectory()) Files.createDirectories(outPath);
                    else {
                        if (outPath.getParent() != null) Files.createDirectories(outPath.getParent());
                        Files.copy(zis, outPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                    zis.closeEntry();
                }
            }
        } catch (IOException e) {
            out.append("unzip error: ").append(e.getMessage()).append(System.lineSeparator());
        }
    }
}

class Parser {
    public ParsedCommand parse(String line) {
        List<String> tokens = new ArrayList<>();
        boolean inQuote = false;
        char quote = 0;
        StringBuilder cur = new StringBuilder();
        for (char c : line.toCharArray()) {
            if (inQuote) {
                if (c == quote) inQuote = false;
                else cur.append(c);
            } else if (c == '"' || c == '\'') {
                inQuote = true;
                quote = c;
            } else if (Character.isWhitespace(c)) {
                if (cur.length() > 0) {
                    tokens.add(cur.toString());
                    cur.setLength(0);
                }
            } else cur.append(c);
        }
        if (cur.length() > 0) tokens.add(cur.toString());
        if (tokens.isEmpty()) return null;

        String redirectTo = null;
        boolean append = false;
        for (int i = tokens.size() - 2; i >= 0; i--) {
            if (tokens.get(i).equals(">") || tokens.get(i).equals(">>")) {
                append = tokens.get(i).equals(">>");
                redirectTo = tokens.get(i + 1);
                tokens = new ArrayList<>(tokens.subList(0, i));
                break;
            }
        }
        String cmd = tokens.get(0);
        List<String> args = tokens.size() > 1 ? tokens.subList(1, tokens.size()) : Collections.emptyList();
        return new ParsedCommand(cmd, new ArrayList<>(args), redirectTo, append);
    }
}

class ParsedCommand {
    public String command;
    public List<String> args;
    public String redirectTo;
    public boolean appendRedirect;

    public ParsedCommand(String c, List<String> a, String r, boolean append) {
        command = c;
        args = a;
        redirectTo = r;
        appendRedirect = append;
    }
}
