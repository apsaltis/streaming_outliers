package com.caseystella.analytics.outlier.streaming.mad;

import com.caseystella.analytics.DataPoint;
import com.caseystella.analytics.Extractor;
import com.caseystella.analytics.outlier.Outlier;
import com.caseystella.analytics.extractor.DataPointExtractor;
import com.caseystella.analytics.extractor.DataPointExtractorConfig;
import com.caseystella.analytics.outlier.streaming.OutlierConfig;
import com.caseystella.analytics.outlier.Severity;
import com.caseystella.analytics.timeseries.OutlierPersister;
import com.caseystella.analytics.util.JSONUtil;
import com.google.common.base.Function;
import org.apache.hadoop.hbase.util.Bytes;

import javax.annotation.Nullable;
import java.io.*;
import java.util.*;

public class OutlierRunner {
    OutlierConfig config;
    Extractor extractor;

    public OutlierRunner(String outlierConfig, String extractorConfig) throws IOException {
        {
            config = JSONUtil.INSTANCE.load(outlierConfig, OutlierConfig.class);
            config.getOutlierAlgorithm().configure(config);
        }
        {
            DataPointExtractorConfig config = JSONUtil.INSTANCE.load(extractorConfig, DataPointExtractorConfig.class);
            extractor = new DataPointExtractor(config);
        }
    }

    public double getMean() {
        return ((SketchyMovingMAD)config.getOutlierAlgorithm()).getValueDistributions().get("benchmark").getCurrentDistribution().getMean();
    }

    public List<Outlier> run(File csv, int linesToSkip, final EnumSet<Severity> reportedSeverities, Function<Map.Entry<DataPoint, Outlier>, Void> callback) throws IOException {
        final List<Outlier> ret = new ArrayList<>();
        config.setOutlierPersisterInstance(new OutlierPersister() {
            @Override
            public void persist(Outlier outlier) {
                if(reportedSeverities.contains(outlier.getSeverity())) {
                    ret.add(outlier);
                }
            }

            @Override
            public void configure(OutlierConfig config, Map<String, Object> context) {

            }
        });
        BufferedReader br = new BufferedReader(new FileReader(csv));
        int numLines = 0;
        for(String line = null;(line = br.readLine()) != null;numLines++){
            if(numLines >= linesToSkip) {
                for(DataPoint dp : extractor.extract(null, Bytes.toBytes(line))) {
                    Outlier o = config.getOutlierAlgorithm().analyze(dp);
                    callback.apply(new AbstractMap.SimpleEntry<>(dp, o));
                    config.getOutlierPersister().persist(o);
                }
            }
        }
        return ret;
    }
    public static Function<Outlier, Long> OUTLIER_TO_TS = new Function<Outlier, Long>() {
        @Nullable
        @Override
        public Long apply(@Nullable Outlier outlier) {
            return outlier.getDataPoint().getTimestamp();
        }
    };
}