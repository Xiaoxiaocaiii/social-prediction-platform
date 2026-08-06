package com.prediction.data;

import java.util.List;

public record PageResult<T>(long total, List<T> items) {
}
