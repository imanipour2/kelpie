# Terminate the supervised PRoot session

An existing runit service may supervise `proot`, with its real long-running
command inside that process. Sending TERM to the supervised PRoot process can
leave its guest command running. A normal `sv restart` then times out and the
guest continues using its previously loaded configuration.

`runit-proot-terminate` is a runit `control/t` hook for that specific case. It
reads the current service's `supervise/pid` and invokes the installed
`proot-distro kill PID` command. It does not search by command name, kill all
containers, change the existing run script, mint credentials or create another
service. The PRoot session registry remains responsible for validating that the
PID belongs to a tracked session and terminating its process tree.

Before installing, verify that the installed `proot-distro kill --help` supports
PID targeting and that the service's current PID is present in `proot-distro ps
-q`. Preserve an existing custom `control/t`; review it instead of overwriting
it. For an absent hook, copy this exact file to the existing service's
`control/t`, mode 0700, and compare SHA-256. The service directory is the working
directory supplied by runsv. Standard Termux executable paths are used.

Validate with one ordinary `sv restart` followed by service status, the existing
tunnel's health checks, and an actual authenticated browser request. The new
supervised PID must replace the old PID; success is not inferred from the hook
exit code alone. No new native computer approval should be needed when the
saved browser trust and header remain valid. Removing only the added hook
restores the previous supervisor behavior.

Installed behavior was inspected against PRoot-Distro 5.10.3. According to its
local help, TERM reaches guest processes; remaining processes can be cleaned up
with QUIT and then KILL. This hook delegates that handling to PRoot-Distro
instead of implementing a second process-tree walker.

Official sources read on 2026-11-20:

- [runit runsv manual](https://smarden.org/runit/runsv.8): an executable
  `control/t` returning zero replaces the default TERM action; a nonzero return
  leaves runsv's standard fallback in place.
- [PRoot-Distro 5.10.3 kill implementation](https://github.com/termux/proot-distro/blob/737306f53fc86213db0ce0ac3e76aa3fda8dcb90/proot_distro/commands/kill.py): `kill PID` targets a
  registered session and its process tree; a bare parent-process signal does
  not provide equivalent cleanup.
- [Termux services](https://github.com/termux/termux-services): existing runit
  service management remains the lifecycle owner.
