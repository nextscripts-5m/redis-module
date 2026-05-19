# Redis Scalability Labs

Labs for `slides/05-Redis Scalability.md` (replication, Sentinel, Cluster, resharding).


| Lab    | Folder                                                      | Topics                                                                                  |
| ------ | ----------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| **01** | [01-sentinel-replication-ha](./01-sentinel-replication-ha/) | 1 master + 3 replicas + 3 Sentinels, stale reads, failover, Grafana per-node write/read |
| **02** | [02-cluster-resharding](./02-cluster-resharding/)           | 3→4 masters, hot-slot load, MOVED/ASK, reshard under load                               |


