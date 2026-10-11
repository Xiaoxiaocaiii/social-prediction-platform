package com.prediction.socialnetwork.reader;

import java.util.List;

public record FileReadResult(List<RawRecord> records, FileReadReport report) {

    public FileReadResult {
        records = List.copyOf(records);
    }
}
