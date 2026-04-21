package com.gemini.bridge.tools

import com.gemini.bridge.termux.TermuxBridge
import com.gemini.bridge.workspace.Workspace
import com.gemini.domain.ToolCall
import com.gemini.domain.ToolCallResult
import com.gemini.domain.ToolCategory
import com.gemini.domain.ToolSpec

/**
 * Runs a shell command inside Termux. When the workspace root maps to a real
 * filesystem path that Termux can see (via `termux-setup-storage`), we chdir
 * into it first so `python foo.py` resolves to files the model just wrote
 * via [WriteFileTool]. Otherwise the command keeps running in Termux's
 * `$HOME` and the model is told about it — file-oriented tasks should then
 * go through the file tools, not `cat`/`grep`.
 */
class RunShellCommandTool(
    private val termux: TermuxBridge,
    private val workspace: Workspace
) : Tool {
    override val spec = ToolSpec(
        name = "run_shell_command",
        description = "Execute a shell command inside Termux. Commands run in the workspace " +
            "directory when it is reachable from Termux (the device path is shown in the " +
            "system instruction); otherwise they run in Termux's \$HOME, so use the file " +
            "tools to read/write workspace files. Requires Termux installed with the " +
            "RUN_COMMAND permission granted. Returns combined stdout and stderr.\n\n" +
            "Foreground commands must return within ~12 s or the call fails with a " +
            "timeout. For anything long-running (dev/web servers, watchers, daemons), " +
            "set `background: true` — the command is detached with nohup, its output " +
            "is redirected to a log file under `~/.gemini-bg/`, and the result returns " +
            "immediately with the PID and log path. You can then check progress with " +
            "`tail -n 80 <log>` and stop the process with `kill <pid>`.",
        category = ToolCategory.SHELL,
        destructive = true,
        parameters = objectParams(
            "command" to stringProp("Command line to execute (e.g. `python script.py`)"),
            "background" to booleanProp(
                "Run in background. Use for servers/daemons/watchers that never return " +
                    "on their own. Returns once the process is confirmed alive (~200 ms), " +
                    "with its PID and log path."
            ),
            required = listOf("command")
        )
    )

    override suspend fun execute(call: ToolCall): ToolCallResult = runCatching {
        val command = call.arguments["command"] as? String ?: error("command is required")
        val background = call.arguments["background"] as? Boolean ?: false
        val workdir = workspace.absolutePath()
        val reason = workspace.unreachableReason()
        val r = termux.run(command, workdir, background = background)
        val body = buildString {
            append("exit=").append(r.exitCode)
            if (background) append("  mode=background")
            if (workdir != null) append("  cwd=").append(workdir)
            else append("  cwd=~  (workspace not reachable from Termux)")
            append('\n')
            if (workdir == null && reason != null) append("reason: ").append(reason).append('\n')
            if (r.stdout.isNotBlank()) append("--- stdout ---\n").append(r.stdout).append('\n')
            if (r.stderr.isNotBlank()) append("--- stderr ---\n").append(r.stderr).append('\n')
        }.trimEnd()
        if (r.ok) ToolOutput.clamp(body, call.id)
        else ToolOutput.error(call.id, body.ifBlank { "shell command failed" })
    }.getOrElse { ToolOutput.error(call.id, it.message ?: "shell failed") }
}
