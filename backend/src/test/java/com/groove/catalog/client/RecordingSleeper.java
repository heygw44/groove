package com.groove.catalog.client;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

final class RecordingSleeper implements Sleeper {

	private final List<Duration> sleptDurations = new ArrayList<>();

	@Override
	public void sleep(Duration duration) {
		sleptDurations.add(duration);
	}

	List<Duration> sleptDurations() {
		return sleptDurations;
	}
}
