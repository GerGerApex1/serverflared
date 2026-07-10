package me.gergerapex1.serverflared.process;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SubCommand {
	String[] command;
	public SubCommand(String... command) {
		this.command = command;
	}
	public List<String> getCommandList() {
		return new ArrayList<>(Arrays.asList(command));
	}
}

