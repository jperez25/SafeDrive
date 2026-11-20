# Git rules
- Always ask the user for confirmation before every `git commit` and every `git push`. Never do either on your own.
  The confirmation is one validation: show the user the commit message and the files to be committed. Once the user
  approves, the AI may run the `git commit` and the `git push` for that change; any other change needs a new validation.
- Every commit message starts with the project key `QNTA-<n>`, where `<n>` is the ticket number, e.g. `QNTA-12: add trip event contract`.
- If the ticket number is not given (`N=`), read the latest commit (`git log -1`) and use its number plus 1 for the
  current ticket (latest `QNTA-12` means this commit is `QNTA-13`). Show the resulting message to the user for validation
  before committing. If the latest commit has no `QNTA-<n>` key, ask the user.
- Never commit `.env` or other secrets.
