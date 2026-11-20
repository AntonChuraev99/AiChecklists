/** Bot commands. Anything unknown answers with /help. */

export const COMMANDS = [
  "start",
  "lists",
  "chat",
  "credits",
  "premium",
  "app",
  "timezone",
  "paysupport",
  "unlink",
  "help",
] as const;
export type Command = (typeof COMMANDS)[number];

/** Command name (already lowercased by parseCommand, "@bot" stripped) → handler key. */
export function resolveCommand(name: string): Command {
  return (COMMANDS as readonly string[]).includes(name) ? (name as Command) : "help";
}
