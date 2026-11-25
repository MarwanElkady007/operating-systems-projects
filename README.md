# Operating systems assignments (Java)

Three assignments from the OS course.

**01-command-line-shell**: a small shell in Java that implements `pwd`, `cd`, `ls`,
`mkdir`, `rmdir`, `touch`, `cp`, `rm`, `cat`, `wc`, `echo`, `zip`, `unzip` and `>` / `>>`
redirection.

**02-service-station-semaphores**: car service station simulation. Cars are threads, the
pumps are a limited resource, and access is controlled with our own semaphore class
(producer/consumer).

**03-cpu-scheduler**: simulates preemptive SJF, Round Robin, and preemptive priority
scheduling with aging, including context-switch time. It prints the execution order and
the waiting and turnaround times.

```
cd 03-cpu-scheduler
javac Scheduler.java && java Scheduler
```

Team project with [Seif Wael](https://github.com/Seiffwaell), [Mohamed Waleed](https://github.com/MrWeza) and Mohamed Kamal.
