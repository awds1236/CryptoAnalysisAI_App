package com.coinsense.cryptoanalysisai.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class TermsContent {

    // 언어에 따라 적절한 문서를 반환하는 메서드
    public static String getTermsOfService(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("crypto_analyze_prefs", Context.MODE_PRIVATE);
        String language = prefs.getString("pref_language", "ko");

        return "en".equals(language) ? TERMS_OF_SERVICE_EN : TERMS_OF_SERVICE_KO;
    }

    public static String getPrivacyPolicy(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("crypto_analyze_prefs", Context.MODE_PRIVATE);
        String language = prefs.getString("pref_language", "ko");

        return "en".equals(language) ? PRIVACY_POLICY_EN : PRIVACY_POLICY_KO;
    }

    public static final String TERMS_OF_SERVICE_KO =
            "# CoinSense 서비스 이용약관\n\n" +
                    "**시행일: 2025년 1월 1일**\n\n" +
                    "## 제1조 (목적)\n" +
                    "본 약관은 CoinSense(이하 \"회사\")가 제공하는 암호화폐 분석 정보 서비스(이하 \"서비스\")의 이용과 관련하여 회사와 회원 간의 권리, 의무 및 책임사항을 규정함을 목적으로 합니다.\n\n" +
                    "## 제2조 (정의)\n" +
                    "1. **\"서비스\"**란 회사가 제공하는 AI 기반 암호화폐 기술적 분석 정보 제공 서비스를 말합니다.\n" +
                    "2. **\"회원\"**이란 본 약관에 따라 이용계약을 체결하고 회사가 제공하는 서비스를 이용하는 자를 말합니다.\n" +
                    "3. **\"분석 정보\"**란 AI에 의해 생성되는 기술적 분석 자료로, 투자 권유나 조언이 아닌 참고용 정보를 의미합니다.\n\n" +
                    "## 제3조 (약관의 효력 및 변경)\n" +
                    "1. 본 약관은 서비스를 이용하고자 하는 모든 회원에 대하여 그 효력을 발생합니다.\n" +
                    "2. 회사는 필요한 경우 관련 법령을 위배하지 않는 범위에서 본 약관을 변경할 수 있습니다.\n" +
                    "3. 약관이 변경된 경우, 회사는 변경사항을 앱 내 공지사항을 통해 공지합니다.\n\n" +
                    "## 제4조 (서비스의 제공 및 변경)\n" +
                    "1. 회사는 다음과 같은 서비스를 제공합니다:\n" +
                    "   - AI 기반 암호화폐 기술적 분석 정보\n" +
                    "   - 가격 추적 및 시장 데이터 제공\n" +
                    "   - 프리미엄 구독 서비스\n" +
                    "2. 회사는 서비스의 개선이나 정책 변경 등을 위해 서비스 내용을 변경할 수 있습니다.\n\n" +
                    "## 제5조 (회원가입)\n" +
                    "1. 회원가입은 Google 계정을 통한 소셜 로그인으로 진행됩니다.\n" +
                    "2. 회원가입 시 본 약관 및 개인정보처리방침에 동의한 것으로 간주됩니다.\n\n" +
                    "## 제6조 (회원의 의무)\n" +
                    "1. 회원은 다음 행위를 하여서는 안 됩니다:\n" +
                    "   - 타인의 정보 도용 또는 허위 정보 입력\n" +
                    "   - 서비스 운영을 방해하는 행위\n" +
                    "   - 관련 법령을 위반하는 행위\n" +
                    "   - 서비스를 이용한 영리활동 또는 상업적 목적의 활동\n" +
                    "2. 회원은 서비스 이용 시 관련 법령과 본 약관을 준수해야 합니다.\n\n" +
                    "## 제7조 (회사의 의무)\n" +
                    "1. 회사는 관련 법령과 본 약관이 금지하거나 공서양속에 반하는 행위를 하지 않으며, 지속적이고 안정적으로 서비스를 제공하기 위해 노력합니다.\n" +
                    "2. 회사는 개인정보보호법 등 관련 법령이 정하는 바에 따라 회원의 개인정보를 보호하기 위해 노력합니다.\n\n" +
                    "## 제8조 (서비스 이용의 제한)\n" +
                    "1. 회사는 다음과 같은 경우 서비스 이용을 제한할 수 있습니다:\n" +
                    "   - 본 약관을 위반한 경우\n" +
                    "   - 서비스의 정상적인 운영을 방해한 경우\n" +
                    "   - 관련 법령을 위반한 경우\n" +
                    "2. 서비스 이용 제한 시 회사는 그 사유를 회원에게 통지합니다.\n\n" +
                    "## 제9조 (유료 서비스)\n" +
                    "1. 회사는 프리미엄 구독 서비스를 제공하며, 이용요금은 앱 내에서 확인할 수 있습니다.\n" +
                    "2. 결제는 Google Play 스토어 정책에 따라 처리됩니다.\n" +
                    "3. 구독 해지는 Google Play 스토어에서 직접 처리해야 합니다.\n\n" +
                    "## 제10조 (서비스 이용 안내)\n" +
                    "1. **투자 관련 안내사항**:\n" +
                    "   - 본 서비스에서 제공하는 모든 정보는 AI에 의한 기술적 분석 참고자료입니다.\n" +
                    "   - 투자 권유, 매매 조언, 수익 보장이 아니며, 투자 결정에 대한 모든 책임은 회원에게 있습니다.\n" +
                    "   - 암호화폐 투자는 높은 위험을 수반하며, 원금 손실 가능성이 있습니다.\n" +
                    "2. **서비스 관련 안내사항**:\n" +
                    "   - 회사는 천재지변, 시스템 장애 등 불가항력으로 인한 서비스 중단에 대해 책임지지 않습니다.\n" +
                    "   - 회원이 서비스를 이용하여 기대하는 수익을 얻지 못하거나 손실을 입은 경우에 대해 책임지지 않습니다.\n\n" +
                    "## 제11조 (분쟁해결)\n" +
                    "1. 서비스 이용으로 발생한 분쟁에 대해 회사와 회원은 성실히 협의하여 해결하도록 노력합니다.\n" +
                    "2. 협의가 이루어지지 않을 경우, 관련 법령에 따라 해결합니다.\n\n" +
                    "## 제12조 (준거법 및 관할법원)\n" +
                    "1. 본 약관의 해석 및 회사와 회원 간의 분쟁에 대하여는 대한민국의 법을 적용합니다.\n" +
                    "2. 서비스 이용으로 발생한 분쟁에 대한 소송은 회사의 본사 소재지를 관할하는 법원에서 진행합니다.\n\n" +
                    "## 부칙\n" +
                    "본 약관은 2025년 6월 12일부터 시행됩니다.\n\n" +
                    "---\n\n" +
                    "**문의처**: coinsense12@gmail.com";

    public static final String TERMS_OF_SERVICE_EN =
            "# CoinSense Terms of Service\n\n" +
                    "**Effective Date: January 1, 2025**\n\n" +
                    "## Article 1 (Purpose)\n" +
                    "These Terms of Service (the \"Terms\") set forth the rights, obligations, and responsibilities between CoinSense (the \"Company\") and users (the \"Members\") regarding the use of cryptocurrency analysis information services (the \"Service\") provided by the Company.\n\n" +
                    "## Article 2 (Definitions)\n" +
                    "1. **\"Service\"** refers to the AI-based cryptocurrency technical analysis information service provided by the Company.\n" +
                    "2. **\"Member\"** refers to a person who has entered into a service agreement in accordance with these Terms and uses the Service provided by the Company.\n" +
                    "3. **\"Analysis Information\"** refers to technical analysis data generated by AI, which is reference information and not investment advice or recommendations.\n\n" +
                    "## Article 3 (Effectiveness and Amendment of Terms)\n" +
                    "1. These Terms shall be effective for all Members who wish to use the Service.\n" +
                    "2. The Company may amend these Terms as necessary within the scope that does not violate relevant laws and regulations.\n" +
                    "3. When the Terms are amended, the Company will notify Members of the changes through in-app notifications.\n\n" +
                    "## Article 4 (Provision and Modification of Service)\n" +
                    "1. The Company provides the following services:\n" +
                    "   - AI-based cryptocurrency technical analysis information\n" +
                    "   - Price tracking and market data provision\n" +
                    "   - Premium subscription services\n" +
                    "2. The Company may modify the Service content for service improvement or policy changes.\n\n" +
                    "## Article 5 (Member Registration)\n" +
                    "1. Member registration is conducted through social login with Google accounts.\n" +
                    "2. By registering as a member, you are deemed to have agreed to these Terms and Privacy Policy.\n\n" +
                    "## Article 6 (Member Obligations)\n" +
                    "1. Members shall not engage in the following activities:\n" +
                    "   - Misappropriation of others' information or input of false information\n" +
                    "   - Activities that interfere with service operation\n" +
                    "   - Activities that violate relevant laws and regulations\n" +
                    "   - Commercial activities or profit-making activities using the Service\n" +
                    "2. Members must comply with relevant laws and these Terms when using the Service.\n\n" +
                    "## Article 7 (Company Obligations)\n" +
                    "1. The Company shall not engage in acts prohibited by relevant laws and these Terms or acts contrary to public order and morals, and shall strive to provide continuous and stable services.\n" +
                    "2. The Company shall strive to protect Members' personal information in accordance with relevant laws such as the Personal Information Protection Act.\n\n" +
                    "## Article 8 (Restrictions on Service Use)\n" +
                    "1. The Company may restrict service use in the following cases:\n" +
                    "   - Violation of these Terms\n" +
                    "   - Interference with normal service operation\n" +
                    "   - Violation of relevant laws and regulations\n" +
                    "2. When restricting service use, the Company will notify the Member of the reason.\n\n" +
                    "## Article 9 (Paid Services)\n" +
                    "1. The Company provides premium subscription services, and usage fees can be confirmed within the app.\n" +
                    "2. Payment processing follows Google Play Store policies.\n" +
                    "3. Subscription cancellation must be processed directly through the Google Play Store.\n\n" +
                    "## Article 10 (Service Usage Guidelines)\n" +
                    "1. **Investment-Related Guidelines**:\n" +
                    "   - All information provided by this Service is AI-generated technical analysis reference material.\n" +
                    "   - This is not investment advice, trading recommendations, or profit guarantees, and all responsibility for investment decisions lies with the Member.\n" +
                    "   - Cryptocurrency investment involves high risks and may result in principal loss.\n" +
                    "2. **Service-Related Guidelines**:\n" +
                    "   - The Company is not responsible for service interruptions due to force majeure such as natural disasters or system failures.\n" +
                    "   - The Company is not responsible for cases where Members fail to achieve expected profits or incur losses through service use.\n\n" +
                    "## Article 11 (Dispute Resolution)\n" +
                    "1. The Company and Members shall make good faith efforts to resolve disputes arising from service use through consultation.\n" +
                    "2. If consultation is not reached, disputes shall be resolved in accordance with relevant laws.\n\n" +
                    "## Article 12 (Governing Law and Jurisdiction)\n" +
                    "1. Korean law shall apply to the interpretation of these Terms and disputes between the Company and Members.\n" +
                    "2. Litigation regarding disputes arising from service use shall be conducted in the court having jurisdiction over the Company's headquarters location.\n\n" +
                    "## Supplementary Provisions\n" +
                    "These Terms shall be effective from June 12, 2025.\n\n" +
                    "---\n\n" +
                    "**Contact**: coinsense12@gmail.com";

    public static final String PRIVACY_POLICY_KO =
            "# CoinSense 개인정보처리방침\n\n" +
                    "**시행일: 2025년 1월 1일**\n\n" +
                    "CoinSense(이하 \"회사\")는 이용자의 개인정보를 중요시하며, 「개인정보보호법」, 「정보통신망 이용촉진 및 정보보호 등에 관한 법률」을 준수하고 있습니다.\n\n" +
                    "## 1. 개인정보의 처리목적\n" +
                    "회사는 다음의 목적을 위하여 개인정보를 처리합니다:\n\n" +
                    "1. **회원가입 및 관리**\n" +
                    "   - 회원제 서비스 이용에 따른 본인확인, 개인식별\n" +
                    "   - 중복가입 확인, 부정이용 방지\n" +
                    "   - 서비스 이용계약 체결 및 유지/종료\n\n" +
                    "2. **서비스 제공**\n" +
                    "   - 암호화폐 분석 정보 제공\n" +
                    "   - 개인맞춤형 서비스 제공\n" +
                    "   - 서비스 이용 현황 분석\n\n" +
                    "3. **요금 결제 및 정산**\n" +
                    "   - 프리미엄 구독 서비스 요금 결제\n" +
                    "   - 구독 관리 및 환불 처리\n\n" +
                    "4. **고객지원**\n" +
                    "   - 문의사항 및 불만 처리\n" +
                    "   - 공지사항 전달\n\n" +
                    "## 2. 개인정보의 처리 및 보유기간\n" +
                    "회사는 법령에 따른 개인정보 보유·이용기간 또는 정보주체로부터 개인정보를 수집 시에 동의받은 개인정보 보유·이용기간 내에서 개인정보를 처리·보유합니다.\n\n" +
                    "1. **회원정보**: 회원탈퇴 시까지 (단, 관계법령 위반에 따른 수사·조사 등이 진행 중인 경우에는 해당 수사·조사 종료 시까지)\n" +
                    "2. **결제정보**: 5년 (전자상거래 등에서의 소비자보호에 관한 법률)\n" +
                    "3. **서비스 이용기록**: 3개월\n\n" +
                    "## 3. 개인정보의 수집항목 및 수집방법\n\n" +
                    "### 수집항목\n" +
                    "1. **필수항목**:\n" +
                    "   - Google 계정 정보 (이메일, 이름, 프로필 사진)\n" +
                    "   - 고유 사용자 식별자(UID)\n" +
                    "   - 서비스 이용기록, 접속 로그, 쿠키, 접속 IP 정보\n\n" +
                    "2. **선택항목**:\n" +
                    "   - 앱 내 설정 정보 (언어, 테마 등)\n" +
                    "   - 구독 서비스 이용 정보\n\n" +
                    "### 수집방법\n" +
                    "- Google 소셜 로그인을 통한 수집\n" +
                    "- 서비스 이용 과정에서 자동 수집\n\n" +
                    "## 4. 개인정보의 제3자 제공\n" +
                    "회사는 원칙적으로 이용자의 개인정보를 외부에 제공하지 않습니다. 다만, 아래의 경우에는 예외로 합니다:\n\n" +
                    "1. 이용자가 사전에 동의한 경우\n" +
                    "2. 법령의 규정에 의거하거나, 수사 목적으로 법령에 정해진 절차와 방법에 따라 수사기관의 요구가 있는 경우\n\n" +
                    "## 5. 개인정보 처리의 위탁\n\n" +
                    "### 위탁업체 및 위탁업무\n" +
                    "1. **Google LLC**\n" +
                    "   - 위탁업무: 로그인 서비스, 결제 서비스\n" +
                    "   - 개인정보보호 수준: GDPR 준수\n\n" +
                    "2. **Firebase (Google)**\n" +
                    "   - 위탁업무: 사용자 인증, 데이터베이스 관리, 분석 서비스\n" +
                    "   - 개인정보보호 수준: GDPR 준수\n\n" +
                    "회사는 위탁계약 체결 시 개인정보 보호법 제26조에 따라 위탁업무 수행목적 외 개인정보 처리금지, 기술적·관리적 보호조치, 재위탁 제한 등을 계약서 등 문서에 명시하고 있습니다.\n\n" +
                    "## 6. 정보주체의 권리·의무 및 그 행사방법\n" +
                    "이용자는 개인정보주체로서 다음과 같은 권리를 행사할 수 있습니다:\n\n" +
                    "1. **개인정보 처리현황 통지 요구**\n" +
                    "2. **개인정보 열람 요구**\n" +
                    "3. **개인정보 정정·삭제 요구**\n" +
                    "4. **개인정보 처리정지 요구**\n\n" +
                    "권리 행사는 회사에 대해 서면, 전화, 전자우편 등을 통하여 하실 수 있으며, 회사는 이에 대해 지체 없이 조치하겠습니다.\n\n" +
                    "## 7. 개인정보의 파기\n" +
                    "회사는 개인정보 보유기간의 경과, 처리목적 달성 등 개인정보가 불필요하게 되었을 때에는 지체없이 해당 개인정보를 파기합니다.\n\n" +
                    "### 파기절차\n" +
                    "이용자가 입력한 정보는 목적 달성 후 별도의 DB에 옮겨져(종이의 경우 별도의 서류) 내부 방침 및 기타 관련 법령에 따라 일정기간 저장된 후 혹은 즉시 파기됩니다.\n\n" +
                    "### 파기방법\n" +
                    "- 전자적 파일 형태: 기록을 재생할 수 없도록 로우레벨포맷(Low Level Format) 등의 방법을 이용하여 파기\n" +
                    "- 종이 문서: 분쇄기로 분쇄하거나 소각하여 파기\n\n" +
                    "## 8. 개인정보의 안전성 확보조치\n" +
                    "회사는 개인정보보호법 제29조에 따라 다음과 같이 안전성 확보에 필요한 기술적/관리적 및 물리적 조치를 하고 있습니다:\n\n" +
                    "1. **개인정보 취급 직원의 최소화 및 교육**\n" +
                    "2. **내부관리계획의 수립 및 시행**\n" +
                    "3. **개인정보의 암호화**\n" +
                    "4. **해킹 등에 대비한 기술적 대책**\n" +
                    "5. **개인정보에 대한 접근 제한**\n\n" +
                    "## 9. 개인정보 자동 수집 장치의 설치·운영 및 거부에 관한 사항\n" +
                    "회사는 이용자에게 개별적인 맞춤서비스를 제공하기 위해 이용정보를 저장하고 수시로 불러오는 '쿠키(cookie)'를 사용합니다.\n\n" +
                    "### 쿠키의 설치·운영 및 거부\n" +
                    "- 쿠키 설정 거부 방법: 웹브라우저에서 옵션을 설정함으로써 모든 쿠키를 허용하거나 쿠키를 저장할 때마다 확인을 거치도록 하거나, 아니면 모든 쿠키의 저장을 거부할 수 있습니다.\n" +
                    "- 단, 쿠키 설치를 거부할 경우 웹 사용이 불편해질 수 있습니다.\n\n" +
                    "## 10. 개인정보 보호책임자\n" +
                    "회사는 개인정보 처리에 관한 업무를 총괄해서 책임지고, 개인정보 처리와 관련한 정보주체의 불만처리 및 피해구제 등을 위하여 아래와 같이 개인정보 보호책임자를 지정하고 있습니다:\n\n" +
                    "**개인정보 보호책임자**\n" +
                    "- 성명: 개인정보보호책임자\n" +
                    "- 연락처: coinsense12@gmail.com\n\n" +
                    "**개인정보 보호담당부서**\n" +
                    "- 부서명: 개발팀\n" +
                    "- 연락처: coinsense12@gmail.com\n\n" +
                    "## 11. 개인정보 처리방침 변경\n" +
                    "이 개인정보처리방침은 시행일로부터 적용되며, 법령 및 방침에 따른 변경내용의 추가, 삭제 및 정정이 있는 경우에는 변경사항의 시행 7일 전부터 공지사항을 통하여 고지할 것입니다.\n\n" +
                    "## 12. 개인정보의 열람청구\n" +
                    "정보주체는 개인정보 보호법 제35조에 따른 개인정보의 열람 청구를 아래의 부서에 할 수 있습니다.\n\n" +
                    "**개인정보 열람청구 접수·처리 부서**\n" +
                    "- 부서명: 개발팀\n" +
                    "- 담당자: 개인정보보호책임자\n" +
                    "- 연락처: coinsense12@gmail.com\n\n" +
                    "## 부칙\n" +
                    "본 방침은 2025년 6월 12일부터 시행됩니다.\n\n" +
                    "---\n\n" +
                    "**문의처**: coinsense12@gmail.com";

    public static final String PRIVACY_POLICY_EN =
            "# CoinSense Privacy Policy\n\n" +
                    "**Effective Date: January 1, 2025**\n\n" +
                    "CoinSense (the \"Company\") values users' personal information and complies with the Personal Information Protection Act and the Act on Promotion of Information and Communications Network Utilization and Information Protection.\n\n" +
                    "## 1. Purpose of Personal Information Processing\n" +
                    "The Company processes personal information for the following purposes:\n\n" +
                    "1. **Member Registration and Management**\n" +
                    "   - Identity verification and personal identification for membership services\n" +
                    "   - Duplicate registration verification and prevention of unauthorized use\n" +
                    "   - Service agreement conclusion and maintenance/termination\n\n" +
                    "2. **Service Provision**\n" +
                    "   - Cryptocurrency analysis information provision\n" +
                    "   - Personalized service provision\n" +
                    "   - Service usage analysis\n\n" +
                    "3. **Payment and Settlement**\n" +
                    "   - Premium subscription service payment processing\n" +
                    "   - Subscription management and refund processing\n\n" +
                    "4. **Customer Support**\n" +
                    "   - Inquiry and complaint handling\n" +
                    "   - Notice delivery\n\n" +
                    "## 2. Personal Information Processing and Retention Period\n" +
                    "The Company processes and retains personal information within the personal information retention and use period prescribed by law or the personal information retention and use period agreed upon when collecting personal information from data subjects.\n\n" +
                    "1. **Member Information**: Until membership withdrawal (However, if investigations or inquiries for violations of relevant laws are in progress, until the completion of such investigations or inquiries)\n" +
                    "2. **Payment Information**: 5 years (Act on Consumer Protection in Electronic Commerce, etc.)\n" +
                    "3. **Service Usage Records**: 3 months\n\n" +
                    "## 3. Personal Information Collection Items and Methods\n\n" +
                    "### Collection Items\n" +
                    "1. **Required Items**:\n" +
                    "   - Google account information (email, name, profile picture)\n" +
                    "   - Unique user identifier (UID)\n" +
                    "   - Service usage records, access logs, cookies, access IP information\n\n" +
                    "2. **Optional Items**:\n" +
                    "   - In-app settings (language, theme, etc.)\n" +
                    "   - Subscription service usage information\n\n" +
                    "### Collection Methods\n" +
                    "- Collection through Google social login\n" +
                    "- Automatic collection during service use\n\n" +
                    "## 4. Third-Party Provision of Personal Information\n" +
                    "The Company does not provide users' personal information to external parties in principle. However, exceptions apply in the following cases:\n\n" +
                    "1. When users have given prior consent\n" +
                    "2. When required by law or when requested by investigative agencies according to procedures and methods prescribed by law for investigative purposes\n\n" +
                    "## 5. Consignment of Personal Information Processing\n\n" +
                    "### Consignees and Consigned Work\n" +
                    "1. **Google LLC**\n" +
                    "   - Consigned Work: Login services, payment services\n" +
                    "   - Personal Information Protection Level: GDPR compliance\n\n" +
                    "2. **Firebase (Google)**\n" +
                    "   - Consigned Work: User authentication, database management, analytics services\n" +
                    "   - Personal Information Protection Level: GDPR compliance\n\n" +
                    "When concluding consignment contracts, the Company specifies in contracts and other documents the prohibition of personal information processing other than for consigned work performance, technical and administrative protection measures, and restrictions on re-consignment in accordance with Article 26 of the Personal Information Protection Act.\n\n" +
                    "## 6. Rights and Obligations of Data Subjects and Exercise Methods\n" +
                    "Users can exercise the following rights as data subjects:\n\n" +
                    "1. **Request for notification of personal information processing status**\n" +
                    "2. **Request for access to personal information**\n" +
                    "3. **Request for correction and deletion of personal information**\n" +
                    "4. **Request for suspension of personal information processing**\n\n" +
                    "Rights can be exercised through written documents, telephone, email, etc. to the Company, and the Company will take action without delay.\n\n" +
                    "## 7. Destruction of Personal Information\n" +
                    "The Company destroys personal information without delay when personal information becomes unnecessary due to expiration of retention period, achievement of processing purpose, etc.\n\n" +
                    "### Destruction Procedure\n" +
                    "Information entered by users is transferred to a separate DB (separate documents in case of paper) after achieving the purpose and stored for a certain period according to internal policies and other relevant laws before destruction or destroyed immediately.\n\n" +
                    "### Destruction Method\n" +
                    "- Electronic file format: Destroyed using methods such as low-level format that make records unrecoverable\n" +
                    "- Paper documents: Destroyed by shredding or incineration\n\n" +
                    "## 8. Measures to Ensure Safety of Personal Information\n" +
                    "The Company implements the following technical, administrative, and physical measures necessary to ensure safety in accordance with Article 29 of the Personal Information Protection Act:\n\n" +
                    "1. **Minimization and education of personal information handling staff**\n" +
                    "2. **Establishment and implementation of internal management plans**\n" +
                    "3. **Encryption of personal information**\n" +
                    "4. **Technical measures against hacking, etc.**\n" +
                    "5. **Restriction of access to personal information**\n\n" +
                    "## 9. Installation, Operation, and Rejection of Automatic Personal Information Collection Devices\n" +
                    "The Company uses 'cookies' that store and retrieve usage information to provide individualized customized services to users.\n\n" +
                    "### Cookie Installation, Operation, and Rejection\n" +
                    "- Cookie setting rejection method: You can allow all cookies, go through confirmation each time cookies are saved, or reject storage of all cookies by setting options in your web browser.\n" +
                    "- However, rejecting cookie installation may make web use inconvenient.\n\n" +
                    "## 10. Personal Information Protection Officer\n" +
                    "The Company designates a Personal Information Protection Officer as follows to take overall responsibility for personal information processing work and handle complaints and damage relief related to personal information processing by data subjects:\n\n" +
                    "**Personal Information Protection Officer**\n" +
                    "- Name: Personal Information Protection Officer\n" +
                    "- Contact: coinsense12@gmail.com\n\n" +
                    "**Personal Information Protection Department**\n" +
                    "- Department: Development Team\n" +
                    "- Contact: coinsense12@gmail.com\n\n" +
                    "## 11. Changes to Privacy Policy\n" +
                    "This Privacy Policy is effective from the effective date, and when there are additions, deletions, and corrections of changes according to laws and policies, changes will be announced through notices 7 days before the implementation of changes.\n\n" +
                    "## 12. Request for Access to Personal Information\n" +
                    "Data subjects can make requests for access to personal information in accordance with Article 35 of the Personal Information Protection Act to the following department:\n\n" +
                    "**Personal Information Access Request Reception and Processing Department**\n" +
                    "- Department: Development Team\n" +
                    "- Person in Charge: Personal Information Protection Officer\n" +
                    "- Contact: coinsense12@gmail.com\n\n" +
                    "## Supplementary Provisions\n" +
                    "This policy is effective from June 12, 2025.\n\n" +
                    "---\n\n" +
                    "**Contact**: coinsense12@gmail.com";
}