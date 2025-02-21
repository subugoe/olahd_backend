package de.ocrd.olahd.domain;

public enum OperandiJobStatus {
    /** First status of a job when request arrives*/
    ACCEPTED,
    /** {@linkplain OperandiJobRunnable} has started to process the job*/
    PREPARING,
    /** Running from Olahd's point of view means the job was submitted to operandi*/
    RUNNING,
    /** Job-status of failed queried from operandi*/
    FAILED,
    /** Job-status of success queried from operandi*/
    SUCCESS,
    /** Job-status from operandi could not be queried due to unknown problems*/
    UNKNOWN,
}
