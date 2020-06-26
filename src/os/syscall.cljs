(ns os.syscall)

;; a placeholder namespace for qualified keywords
;; why not some documentation too?
;;  sys-calls is a vector of sys-call vectors
;;  a sys-call is vector of one of the following:
;;   `[:kernel/start-process :child-pid process-type initial-mem]`
;;   `[:kernel/kill-process pid]`
;; syscalls are executed in order
