## Purpose

How long and how many finished reports the service keeps in memory for download. Running reports are bounded by `max-queued-reports`, outside this spec.

## ADDED Requirements

### Requirement: Finished reports are bounded by count

The service SHALL keep at most `license-service.max-stored-reports` finished reports. Past it, the oldest finished reports SHALL be dropped before their TTL ends; the report that just finished SHALL always be kept.

#### Scenario: Over the count

- **WHEN** `max-stored-reports` is 2, two reports are finished, and a third finishes
- **THEN** the oldest is gone and the other two can be downloaded

### Requirement: The byte limit drops only reports that hold files

When the license files of finished reports exceed `license-service.max-stored-bytes`, the service SHALL drop, oldest first, only finished reports that hold files, until the total is within the limit.

#### Scenario: An excluded report is not dropped for bytes

- **WHEN** the oldest finished report is `EXCLUDED`, the next holds files, and the limit is exceeded
- **THEN** the `EXCLUDED` report is kept and the one with files is dropped
