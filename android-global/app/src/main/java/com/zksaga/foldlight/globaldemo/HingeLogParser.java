package com.zksaga.foldlight.globaldemo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Only the two angle fields observed in this Samsung firmware, never arbitrary log content. */
final class HingeLogParser {
    private static final Pattern FOLD=Pattern.compile("\\[0\\]folding_angle ts=(\\d+) ns value \\[\\s*([0-9.]+)/");
    private static final Pattern LID=Pattern.compile("\\[0\\]lid_angle_fusion ts=(\\d+) ns value \\[\\d+/\\s*([0-9.]+)/");
    static final class Sample {
        final long nanos; final float angle;
        Sample(long nanos,float angle){this.nanos=nanos;this.angle=angle;}
    }
    static Sample parse(String line){
        if(!line.contains("handle_sns_client_event:"))return null;
        Matcher m=FOLD.matcher(line);if(!m.find()){m=LID.matcher(line);if(!m.find())return null;}
        try{long ns=Long.parseLong(m.group(1));float a=Float.parseFloat(m.group(2));return ns>0&&FoldMath.validAngle(a)?new Sample(ns,a):null;}
        catch(NumberFormatException e){return null;}
    }
    static boolean fresh(long stamp,long now){return stamp>0&&stamp<=now+50_000_000L&&stamp>=now-500_000_000L;}
}
