package me.gergerapex1.serverflared.process;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record SubCommand(String... command) {
    public List<String> getCommandList() {
        return new ArrayList<>(Arrays.asList(command));
    }
}
