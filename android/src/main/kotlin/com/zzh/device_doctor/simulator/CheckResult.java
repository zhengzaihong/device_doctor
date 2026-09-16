package com.zzh.device_doctor.simulator;

public class CheckResult {
    /** 0 = 可能是模拟器 */
    public static final int RESULT_MAYBE_EMULATOR = 0;
    /** 1 = 强命中模拟器 */
    public static final int RESULT_EMULATOR = 1;
    /** 2 = 未命中，倾向真机 */
    public static final int RESULT_UNKNOWN = 2;
    /** 可读别名：与 RESULT_UNKNOWN 同值，语义更清 */
    public static final int RESULT_LIKELY_REAL_DEVICE = RESULT_UNKNOWN;

    public int result;
    public String value;

    public CheckResult(int result, String value) {
        this.result = result;
        this.value = value;
    }
}
