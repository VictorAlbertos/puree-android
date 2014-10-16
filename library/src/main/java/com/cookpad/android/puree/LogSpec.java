package com.cookpad.android.puree;

import android.util.Pair;

import com.cookpad.android.puree.outputs.PureeOutput;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class LogSpec {
    private static final Object LOCK = new Object();

    private PureeConfiguration conf;
    private List<Pair<JsonConvertible, String[]>> logs = new ArrayList<>();
    private String targetType;

    public LogSpec(PureeConfiguration conf) {
        this.conf = conf;
    }

    public LogSpec log(final JsonConvertible log, final String... sendTo) {
        this.logs.add(new Pair<>(log, sendTo));
        return this;
    }

    public LogSpec targetType(String targetType) {
        this.targetType = targetType;
        return this;
    }

    public void shouldBe(Matcher matcher) {
        synchronized (LOCK) {
            final CountDownLatch latch = new CountDownLatch(logs.size());
            final List<JSONObject> results = new ArrayList<>();

            initializePuree(conf);

            for (PureeOutput output : conf.getOutputs()) {
                output.setEmitCallback(new EmitCallback() {
                    @Override
                    public void call(String type, List<JSONObject> serializedLogs) {
                        if (targetType.equals(type)) {
                            results.addAll(serializedLogs);
                        }
                        latch.countDown();
                    }
                });
            }

            putLogs(logs);

            try {
                latch.await(1000, TimeUnit.MILLISECONDS);
                matcher.expect(results);
            } catch (JSONException | InterruptedException e) {
                throw new RuntimeException(e.getMessage());
            }
        }
    }

    private void putLogs(List<Pair<JsonConvertible, String[]>> logs) {
        for (Pair<JsonConvertible, String[]> log : logs) {
            Puree.send(log.first, log.second);
        }
    }

    private void initializePuree(PureeConfiguration conf) {
        PureeConfiguration.isTest = true;
        Puree.initialize(conf);
        Puree.clear();
    }

    public static interface Matcher {
        public void expect(List<JSONObject> serializedLogs) throws JSONException;
    }
}