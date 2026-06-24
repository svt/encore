# Contributing

We gratefully accept contributions via [pull requests](https://help.github.com/articles/about-pull-requests/).

Use the issue tracker to suggest feature requests, report bugs, and ask questions.
This is also a great way to connect with the developers of the project as well
as others who are interested in this solution.

## Changing the code-base

Generally speaking, you should fork this repository, make changes in your
own fork, and then submit a pull-request. This is often called the [Fork-and-Pull model](https://gist.github.com/Chaser324/ce0505fbed06b947d962).

- All contributions to this project will be released under the inbound=outbound norm, that is,
  they are submitted under the project's main license.
- By submitting a pull request or filing a bug, issue, or
  feature request, you agree to comply with this waiver of copyright interest.
  Details can be found in the [LICENSE](./LICENSE).
- All new code should have associated unit
  tests that validate implemented features and the presence or lack of defects.
- Additionally, the code should follow any stylistic and architectural guidelines
  prescribed by the project. In the absence of such guidelines, mimic the styles
  and patterns in the existing code-base.

### Signoff and optionally sign each commit

As part of filing a pull request you agree to the [Developer Certificate of Origin](https://developercertificate.org/) (DCO).

A DCO is a lightweight way for a contributor to confirm that they wrote or otherwise have the right
to submit code or documentation to a project.

To confirm that you agree to the DCO, you need to **sign off** your commits when sending us a pull request. Technically, this is done by supplying the `-s`/`--signoff` flag to git when committing:

```
git commit -s -m "add fix for the bug"
```

Optionally, you can also sign the commit with `-S` which also gives your commit a nice verified button on GitHub,
but it requires that you have a GPG keypair set up.
For more information, see [Sign commit on GitHub with GPG key](https://docs.github.com/en/github/authenticating-to-github/signing-commits).

```
git commit -s -S -m "add fix for the bug"
```

For the difference in signoff and signing, see
[Git signoff vs signing](https://medium.com/@MarkEmeis/git-commit-signoff-vs-signing-9f37ee272b14).

## Git history

In order to maintain a high software quality standard, we strongly prefer contributions to follow these rules:

- We pay more attention to the quality of commit messages. In general, we share the view on how commit messages should be written with
  [the Git project itself](https://github.com/git/git/blob/master/Documentation/SubmittingPatches):

- [Make separate commits for logically separate changes.](https://github.com/git/git/blob/e6932248fcb41fb94a0be484050881e03c7eb298/Documentation/SubmittingPatches#L43)
  For example, pure formatting changes that do not affect software behaviour usually do not belong in the same commit as
  changes to program logic.

- [Describe your changes well.](https://github.com/git/git/blob/e6932248fcb41fb94a0be484050881e03c7eb298/Documentation/SubmittingPatches#L101)
  Do not just repeat in prose what is "obvious" from the code, but provide a rationale explaining _why_ you believe
  your change is necessary.

- [Describe your changes in the imperative.](https://github.com/git/git/blob/e6932248fcb41fb94a0be484050881e03c7eb298/Documentation/SubmittingPatches#L133)
  Instead of writing "Fixes an issue with encoding" prefer "Fix an encoding issue". Think about it like the commit
  only does something _if_ it is applied. This usually results in more concise commit messages.

- [We are picky about whitespaces.](https://github.com/git/git/blob/e6932248fcb41fb94a0be484050881e03c7eb298/Documentation/SubmittingPatches#L95)
  Trailing whitespace and duplicate blank lines are simply a superfluous annoyance, and most Git tools flag them red
  in the diff anyway.

If you have ever wondered how a "perfect" commit message is supposed to look like, just look at basically any of
[Jeff King's commits](https://github.com/git/git/commits?author=peff) in the Git project.

Thank you for reading and happy contributing!
