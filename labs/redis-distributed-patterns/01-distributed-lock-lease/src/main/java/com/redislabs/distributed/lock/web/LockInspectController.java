package com.redislabs.distributed.lock.web;

import com.redislabs.distributed.lock.lock.DistributedLockService;
import com.redislabs.distributed.lock.lock.LockSnapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/locks")
public class LockInspectController {

    private final DistributedLockService lockService;

    public LockInspectController(DistributedLockService lockService) {
        this.lockService = lockService;
    }

    @GetMapping("/{orderId}")
    public LockSnapshot inspect(@PathVariable String orderId) {
        return lockService.inspect(orderId);
    }
}
