---
description: Create a git commit with a short message
allowed-tools: Bash(git add:*), Bash(git status:*), Bash(git commit:*)
argument-hint: [message]
model: claude-haiku-4-5
---

Create a git commit describing current changes, with message based on my short description: $ARGUMENTS (if empty, just create your own short description based on changes made in files)
