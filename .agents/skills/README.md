# Project skills

This directory holds skills for coding agents that work in this repository. A skill is a set of instructions for
one task, such as making a commit. The skills use the [Agent Skills](https://agentskills.io) format, so any agent that
supports the format can use them. An agent that doesn't can read a `SKILL.md` like any other instruction file.

`.claude/skills` is a symbolic link to this directory, so Claude Code finds the skills in the location it reads.
Add skills here, not under `.claude/`.

Each skill is a directory with a `SKILL.md`, plus any supporting files it links to:

```
.agents/skills/<skill-name>/SKILL.md
```

`SKILL.md` starts with frontmatter, followed by the instructions:

```markdown
---
name: skill-name
description: What the skill does. Use when <the situations that should trigger it>.
---

Instructions for the agent.
```

`name` must equal the directory name. Agents decide whether to load a skill from its `description` alone, so say
when to use it there. Keep the instructions agent-neutral: name no specific agent or tool unless the step needs it.
