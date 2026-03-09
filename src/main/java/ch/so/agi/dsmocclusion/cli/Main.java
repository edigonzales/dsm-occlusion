package ch.so.agi.dsmocclusion.cli;

import picocli.CommandLine;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new OcclusionCommand()).execute(args);
        System.exit(exitCode);
    }
}
