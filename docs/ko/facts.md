# 천상 2.0.0 사실표

문서에 쓰는 숫자의 원본입니다. 다른 문서의 숫자는 이 표에서 옮겨 씁니다.
버전업 때 `_tools/make_facts.py`를 실행해 갱신합니다. "근거"가 파일 이름인 값은 스크립트가 직접 센 값이고, 나머지는 대조 스크립트 결과를 옮긴 값입니다.

| 분야 | 항목 | 값 | 근거 |
|---|---|---|---|
| 버전 | 천상 | 2.0.0 | gradle.properties |
| 버전 | cheonsang-extra | 1.0.1 | _테스트배포 파일명 |
| 환경 | Minecraft | 26.3 | gradle.properties |
| 환경 | Fabric Loader | 0.20.1 이상 | gradle.properties |
| 환경 | Fabric API | 0.165.0+26.3 | gradle.properties |
| 환경 | Fabric Language Kotlin | 1.14.2+kotlin.2.4.21 | gradle.properties |
| 파일 | cheonsang-2.0.0.jar | 111.5 MB | build/libs |
| 파일 | cheonsang-extra-1.0.1.jar | 434.0 MB | _테스트배포 |
| 별 | 맨눈 별 (stars.bin) | 90,938개 | stars.bin 머리 개수 |
| 별 | Gaia 별 (extra 팩) | 32,061,510개, V 15.2등까지 | CREDITS.txt |
| 별 | 이름 붙은 유명 별 (고유명 없는 별) | 18개 | famous_stars.json |
| 별 | 망원경 별 (deep_stars.bin) | 2,409,260개, 15.37등까지 | make_deep_stars.py 결과 (2.0.0 jar 측정) |
| 별 | WDS 근접 쌍성 반성 | 7,459개 (HYG 9등 83,479 + 반성 = 맨눈 별) | 2.0.0 jar 측정 |
| 별자리 | 서양 별자리 선 | 88개 (+ 뱀자리 분할 1) | constellations.json |
| 별자리 | 천상열차분야지도 별자리 | 289개 | korean_constellations.json |
| 심원천체 | 전체 | 554개 | dso.json |
| 심원천체 | 메시에 / 칼드웰 | 110 / 109 | dso.json id·caldwell |
| 달 | 달 지명 | 787곳 | moon_names.json |
| 천문현상 | 유성우 | 7개 + 산발 시간당 10개 | meteors.json |
| 계산 | 행성 VSOP87 항 수 | 1,259항 (진폭 3×10⁻⁶ rad 이상) | Vsop87Data.kt |
| 정확도 | 행성 최대 오차 1800\~2050 (PyEphem 대조) | 수성 4.3″ · 금성 5.4″ · 화성 12.6″ · 목성 4.3″ · 토성 3.8″ · 천왕성 4.0″ · 해왕성 2.4″ | _tools/vsop_check.py |
| 정확도 | 행성 최대 오차 기원전 1000\~서기 3000 | 약 2′ | _tools/vsop_check.py |
| 정확도 | 해·달 최대 위치 오차 1900\~2100 (400시점) | 해 43.7″ · 달 30.0″ | verify_all.py |
| 정확도 | 별 | J2000 + 세차만 (장동·광행차·고유운동 없음), Stellarium 대비 약 1′ | 결정사항_정리 |
| 정확도 | 달 물리칭동·장동 생략 오차 | 0.04° 안팎 | Moon.kt |
| 광학 | 회절 반지름 | 138″/D(mm) | Optics.kt |
| 광학 | 한계등급 (맨눈 6.0 기준) | 80mm 11.7등 · 305mm 14.6등 | Optics.kt |
| 광학 | 구경 / 접안 | 50\~305mm / 40\~3mm | TelescopeView.kt |
| 성능 | 행성 7개 계산 1회 | 28µs | 2.0.0 측정 (2000회 평균) |
| 라이선스 | 코드 | MIT | LICENSE |
| 라이선스 | DSS 스케치 판 | CC BY-NC-SA 4.0 (비상업) | STScI |
