package com.coinsense.cryptoanalysisai.utils;
import android.content.Context;

import com.coinsense.cryptoanalysisai.BuildConfig;
import com.coinsense.cryptoanalysisai.R;

public class Constants {

    // API 기본 URL
    public static final String UPBIT_API_URL = "https://api.upbit.com/v1/";
    public static final String BINANCE_API_URL = "https://api.binance.com/api/v3/";

    // AWS Lambda API 기본 URL (API Gateway 엔드포인트)
    public static final String LAMBDA_API_URL = BuildConfig.LAMBDA_API_URL;

    // 로그인 및 사용자 관련 상수
    public static final String PREF_IS_LOGGED_IN = "pref_is_logged_in";
    public static final String PREF_USER_EMAIL = "pref_user_email";
    public static final String PREF_USER_DISPLAY_NAME = "pref_user_display_name";
    public static final String PREF_USER_ID = "pref_user_id";

    // 구독 관련 상수
    public static final String PREF_IS_SUBSCRIBED = "pref_is_subscribed";
    public static final String PREF_SUBSCRIPTION_EXPIRY = "pref_subscription_expiry";
    public static final String PREF_SUBSCRIPTION_TYPE = "pref_subscription_type";
    public static final String PREF_SUBSCRIPTION_START_TIME = "pref_subscription_start_time";
    public static final String PREF_SUBSCRIPTION_AUTO_RENEWING = "pref_subscription_auto_renewing";
    public static final String PREF_MONTHLY_PRICE = "pref_monthly_price";
    public static final String PREF_YEARLY_PRICE = "pref_yearly_price";

    // 구독 유형
    public static final String SUBSCRIPTION_MONTHLY = "monthly";
    public static final String SUBSCRIPTION_YEARLY = "yearly";
    public static final String SUBSCRIPTION_NONE = "none";

    // AWS RDS 설정 - 미사용 (Lambda API로 대체)
    // public static final String DB_URL = BuildConfig.DB_URL;
    // public static final String DB_USER = BuildConfig.DB_USER;
    // public static final String DB_PASS = BuildConfig.DB_PASS;

    // To this
    public static final String[] MAIN_COINS = {"BTC", "ETH", "XRP", "SOL","DOGE", "ADA"};

    // Add a new array for premium coins
    public static final String[] PREMIUM_COINS = {"TRX", "SUI", "LINK", "AVAX", "XLM", "HBAR", "HYPE", "BCH", "TON", "LTC", "DOT", "UNI", "AAVE", "NEAR","POL","VET"};

    // 기본 설정
    public static final int DEFAULT_CANDLE_COUNT = 30;
    public static final String DEFAULT_MARKET = "KRW-BTC";
    public static final String DEFAULT_BINANCE_SYMBOL = "BTCUSDT";

    public static final int PRICE_REFRESH_INTERVAL = 3000; // 3초

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

    public static boolean isBasicCoin(String symbol) {
        if (symbol == null) return false;

        for (String coin : MAIN_COINS) {
            if (coin.equalsIgnoreCase(symbol)) {
                return true;
            }
        }
        return false;
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
        STRONG_BUY(R.string.strong_buy_recommendation, android.graphics.Color.rgb(76, 175, 80)),    // 진한 초록색
        WEAK_BUY(R.string.weak_buy_recommendation, android.graphics.Color.rgb(139, 195, 74)),       // 연한 초록색
        STRONG_SELL(R.string.strong_sell_recommendation, android.graphics.Color.rgb(244, 67, 54)),  // 빨간색
        WEAK_SELL(R.string.weak_sell_recommendation, android.graphics.Color.rgb(255, 152, 0)),      // 주황색
        HOLD(R.string.hold_recommendation, android.graphics.Color.rgb(255, 193, 7));

        private final int displayNameResId;
        private final int color;

        RecommendationType(int displayNameResId, int color) {
            this.displayNameResId = displayNameResId;
            this.color = color;
        }

        public int getDisplayNameResId() {
            return displayNameResId;
        }

        public String getDisplayName(Context context) {
            return context.getString(displayNameResId);
        }

        public int getColor() {
            return color;
        }

        public static RecommendationType fromString(String value) {
            if (value == null) {
                return HOLD; // 기본값
            }

            // 값을 소문자로 변환하여 비교 (대소문자 구분 없이)
            String lowerValue = value.toLowerCase().trim();

            // 강한 상승 관련 키워드
            if (lowerValue.contains("강한 상승") || lowerValue.contains("strong buy") ||
                    lowerValue.contains("매수") || lowerValue.contains("buy") ||
                    (lowerValue.contains("상승") && !lowerValue.contains("약한"))) {
                return STRONG_BUY;
            }
            // 약한 상승 관련 키워드
            else if (lowerValue.contains("약한 상승") || lowerValue.contains("mild upward") ||
                    lowerValue.contains("weak buy") || lowerValue.contains("slight upward")) {
                return WEAK_BUY;
            }
            // 강한 하락 관련 키워드
            else if (lowerValue.contains("강한 하락") || lowerValue.contains("strong sell") ||
                    lowerValue.contains("매도") || lowerValue.contains("sell") ||
                    (lowerValue.contains("하락") && !lowerValue.contains("약한"))) {
                return STRONG_SELL;
            }
            // 약한 하락 관련 키워드
            else if (lowerValue.contains("약한 하락") || lowerValue.contains("mild downward") ||
                    lowerValue.contains("weak sell") || lowerValue.contains("slight downward")) {
                return WEAK_SELL;
            }
            // 보합/중립 관련 키워드
            else if (lowerValue.contains("보합") || lowerValue.contains("sideways") ||
                    lowerValue.contains("hold") || lowerValue.contains("neutral") ||
                    lowerValue.contains("wait") || lowerValue.contains("관망")) {
                return HOLD;
            }

            // 기본값은 HOLD
            return HOLD;
        }

        public static RecommendationType fromProbabilities(double buyProbability, double sellProbability) {
            if (buyProbability >= 70) {
                return STRONG_BUY;
            } else if (buyProbability >= 50) {  // 60%는 여기에 해당해야 함
                return WEAK_BUY;
            } else if (sellProbability >= 70) {
                return STRONG_SELL;
            } else if (sellProbability >= 50) {
                return WEAK_SELL;
            } else {
                return HOLD;
            }
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