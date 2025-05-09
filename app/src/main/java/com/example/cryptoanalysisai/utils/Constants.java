package com.example.cryptoanalysisai.utils;
import com.example.cryptoanalysisai.BuildConfig;
public class Constants {

    // API 기본 URL
    public static final String UPBIT_API_URL = "https://api.upbit.com/v1/";
    public static final String BINANCE_API_URL = "https://api.binance.com/api/v3/";
    public static final String CLAUDE_API_URL = "https://api.anthropic.com/";

    // API 키 (실제 앱에서는 BuildConfig나 암호화하여 보관해야 함)
    public static final String CLAUDE_API_KEY = BuildConfig.CLAUDE_API_KEY; // TODO: API 키 설정 필요

    // 기본 설정
    public static final int DEFAULT_CANDLE_COUNT = 30;
    public static final String DEFAULT_MARKET = "KRW-BTC";
    public static final String DEFAULT_BINANCE_SYMBOL = "BTCUSDT";

    // 캔들 차트 간격
    public enum CandleInterval {
        MINUTE_1("1m", "minutes/1"),
        MINUTE_3("3m", "minutes/3"),
        MINUTE_5("5m", "minutes/5"),
        MINUTE_15("15m", "minutes/15"),
        MINUTE_30("30m", "minutes/30"),
        HOUR_1("1h", "minutes/60"),
        HOUR_4("4h", "minutes/240"),
        DAY_1("1d", "days"),
        WEEK_1("1w", "weeks"),
        MONTH_1("1M", "months");

        private final String binanceCode;
        private final String upbitCode;

        CandleInterval(String binanceCode, String upbitCode) {
            this.binanceCode = binanceCode;
            this.upbitCode = upbitCode;
        }

        public String getBinanceCode() {
            return binanceCode;
        }

        public String getUpbitCode() {
            return upbitCode;
        }

        public String getDisplayName() {
            switch (this) {
                case MINUTE_1: return "1분";
                case MINUTE_3: return "3분";
                case MINUTE_5: return "5분";
                case MINUTE_15: return "15분";
                case MINUTE_30: return "30분";
                case HOUR_1: return "1시간";
                case HOUR_4: return "4시간";
                case DAY_1: return "일봉";
                case WEEK_1: return "주봉";
                case MONTH_1: return "월봉";
                default: return "";
            }
        }
    }

    // 기술적 지표
    public enum TechnicalIndicator {
        RSI("RSI", "상대강도지수"),
        MACD("MACD", "이동평균수렴확산"),
        SMA("SMA", "단순이동평균"),
        EMA("EMA", "지수이동평균"),
        BOLLINGER("BB", "볼린저밴드");

        private final String code;
        private final String displayName;

        TechnicalIndicator(String code, String displayName) {
            this.code = code;
            this.displayName = displayName;
        }

        public String getCode() {
            return code;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // 추천 유형
    public enum RecommendationType {
        BUY("매수", android.graphics.Color.rgb(76, 175, 80)),  // 초록색
        SELL("매도", android.graphics.Color.rgb(244, 67, 54)), // 빨간색
        HOLD("관망", android.graphics.Color.rgb(255, 152, 0)); // 주황색

        private final String displayName;
        private final int color;

        RecommendationType(String displayName, int color) {
            this.displayName = displayName;
            this.color = color;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getColor() {
            return color;
        }

        public static RecommendationType fromString(String value) {
            if (value != null) {
                for (RecommendationType type : RecommendationType.values()) {
                    if (type.displayName.equalsIgnoreCase(value)) {
                        return type;
                    }
                }
            }
            return HOLD; // 기본값
        }
    }

    // 추세 강도
    public enum TrendStrength {
        STRONG("강", android.graphics.Color.rgb(76, 175, 80)),  // 초록색
        MEDIUM("중", android.graphics.Color.rgb(255, 152, 0)), // 주황색
        WEAK("약", android.graphics.Color.rgb(244, 67, 54));   // 빨간색

        private final String displayName;
        private final int color;

        TrendStrength(String displayName, int color) {
            this.displayName = displayName;
            this.color = color;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getColor() {
            return color;
        }

        public static TrendStrength fromString(String value) {
            if (value != null) {
                for (TrendStrength strength : TrendStrength.values()) {
                    if (strength.displayName.equals(value)) {
                        return strength;
                    }
                }
            }
            return MEDIUM; // 기본값
        }
    }

    // 인텐트 키
    public static final String EXTRA_COIN_INFO = "extra_coin_info";
    public static final String EXTRA_EXCHANGE_TYPE = "extra_exchange_type";
    public static final String EXTRA_ANALYSIS_RESULT = "extra_analysis_result";

    // 프래그먼트 태그
    public static final String TAG_COIN_LIST = "tag_coin_list";
    public static final String TAG_CHART = "tag_chart";
    public static final String TAG_ANALYSIS = "tag_analysis";

    // SharedPreferences 키
    public static final String PREFS_NAME = "crypto_analyze_prefs";
    public static final String PREF_EXCHANGE_TYPE = "pref_exchange_type";
    public static final String PREF_LAST_MARKET = "pref_last_market";
}