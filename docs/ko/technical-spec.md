# 천상 2.0.0 기술 사양서

천상 2.0.0 기준 · 2026-11-15

## 목차

1. 개요
2. 실행 환경과 빌드 구성
3. 모듈 구성
4. 천문 계산 모델
5. 데이터 출처와 가공
6. 렌더링
7. 성능
8. 명령어·키·설정
9. 멀티플레이 동기화와 악용 방지
10. 장애 격리
11. 알려진 제한

---

## 1. 개요

천상(Cheonsang)은 Minecraft Java Edition 오버월드의 하늘을 천문 계산 결과로 대체하는 Fabric 모드입니다. 관측 위치(위도·경도)와 하늘 시각(율리우스일)을 입력으로 받아 별·해·달·행성·위성·소행성·심원천체의 방향과 겉보기 밝기를 구하고, 대기·하늘 밝기·망원경 광학 조건으로 가시성을 판정한 뒤 GPU 셰이더로 그립니다.

| 항목 | 값 |
| --- | --- |
| 모드 ID | `cheonsang` (확장팩 `cheonsang_extra`) |
| 패키지 | `kr.inf.cheonsang` |
| 실행 환경 | `environment: "*"` (서버·클라이언트 공통 jar, 클라이언트 단독 실행 가능) |
| 라이선스 | 코드 MIT. 데이터 파일은 원 자료의 라이선스를 따름 (CREDITS.txt, 5.14절) |
| 소스 규모 | Kotlin 56개·Java 13개 파일, 10,918줄 (VSOP87 계수 파일 포함), GLSL 셰이더 11개 |
| 번역 | `ko_kr`·`en_us` 각 546개 키. 게임 언어가 한국어면 한국어, 그 밖에는 영어 |
| 발전과제 | 24개 (`data/cheonsang/advancement/`) |
| 모드 아이콘 | `assets/cheonsang/icon.png` 128×128 (직접 그린 그림, CC BY-SA 4.0). 확장팩은 `assets/cheonsang_extra/icon.png` 128×128 |
| 배포 파일 크기 | 본체 `cheonsang-2.0.0.jar` 111.5 MB, 확장팩 `cheonsang-extra-1.0.1.jar` 434.0 MB |

---

## 2. 실행 환경과 빌드 구성

값은 `gradle.properties`, `build.gradle.kts`, `fabric.mod.json`에서 확인했습니다.

| 항목 | 값 |
| --- | --- |
| Minecraft | 26.3 (`"minecraft": "~26.3"`) |
| Fabric Loader | 0.20.1 이상 (`>=0.20.1`) |
| Fabric API | 0.165.0+26.3 |
| Fabric Language Kotlin | 1.14.2+kotlin.2.4.21 |
| Kotlin | 2.4.21 (JVM 타깃 25) |
| Java | 25 (`options.release = 25`, 요구: `>=25`) |
| Fabric Loom | 1.19-SNAPSHOT (`net.fabricmc.fabric-loom`, `splitEnvironmentSourceSets`) |
| Mod Menu | 21.0.0 (선택, 설정 화면 진입점) |
| 충돌 선언 | `"breaks": { "iris": "*" }`. Iris와 함께 설치하면 게임이 시작되지 않음 |
| 확장팩 | cheonsang-extra 1.0.1. 의존성 `cheonsang >=1.10.2`, `minecraft ~26.3`, `environment: "client"` |

---

## 3. 모듈 구성

### 3.1 진입점과 패키지

| 진입점 | 클래스 | 역할 |
| --- | --- | --- |
| `main` | `kr.inf.cheonsang.Cheonsang` | 서버·공통 초기화: `SkyServer`, `LogServer`, `PoseServer`, `VanillaSync`, `Telescope` 등록 |
| `client` | `kr.inf.cheonsang.client.CheonsangClient` | 렌더링, 클라이언트 명령, 키 매핑, 패킷 수신, 설정 파일 |
| `modmenu` | `kr.inf.cheonsang.client.ModMenuEntry` | Mod Menu 설정 화면 연결 |

| 소스 세트 | 패키지 | 주요 구성 |
| --- | --- | --- |
| main (공통) | `kr.inf.cheonsang` | `SkyServer`(하늘 상태·`/cele`), `LogServer`(관측 도감), `PoseServer`(망원경 자세), `RateLimit`, `RealTime`(실시간 도시 13곳), `VanillaSync`(바닐라 달 위상·밤), `Telescope`(아이템), `NextEvents`, `L`(번역) |
| main (공통) | `kr.inf.cheonsang.astro` | `AstroMath`(벡터·행렬·ΔT), `Calendar`, `SkyClock`, `Frames`, `Sun`, `Moon`·`MoonTerms`, `Planets`·`Vsop87Data`, `Eclipse`, `RiseSet`, `Events`, `WhenCalc` |
| client | `kr.inf.cheonsang.client` | `TelescopeView`·`TelescopeHud`·`Optics`(망원경), `Visibility`(가시성 모델), `AmbientLight`(빛공해·암순응·오로라 조건), `InfoCard`, `FindGuide`(`/cfind`), `CWhen`(`/cwhen`), `Place`(`/cplace`), `LogBook`·`NotebookScreen`, `SettingsScreen`, `Bench`, `Safety`, `PoseSync` |
| client | `kr.inf.cheonsang.client.sky` | `CheonsangSky`(하늘 렌더 본체), `StarCatalog`, `DeepStars`, `TileSky`, `PhotoSky`, `GalaxySketch`, `Meteors`, `SmallBodies`, `SkyData`, `Packs`, `VramBudget`, `VisualDay`, `GrayTexture` |

천문 계산(`astro`)은 공통 소스 세트에 있어 서버 명령(`/cele`, 다음 현상 계산)과 클라이언트 렌더링이 같은 코드를 씁니다.

### 3.2 믹스인

| 구성 파일 | 대상 |
| --- | --- |
| `cheonsang.mixins.json` (공통) | `EnvironmentAttributeSystemMixin` |
| `cheonsang.client.mixins.json` | `SkyRendererMixin`, `CameraMixin`, `MouseHandlerMixin`, `MinecraftMixin`, `AtmosphericFogMixin`, `LightmapMixin`, `AvatarRendererMixin`, `EntityRendererAccessor`, `FirstPersonHandsMixin`, `TimelineMixin`, `AttributeTrackSamplerMixin` |

화면 낮밤은 26.3 `day` 타임라인의 화면 트랙(하늘·안개·구름색·땅의 하늘빛·별 밝기·노을)만 천상 해 고도에 맞춘 가상 시각으로 표본화합니다(`TimelineMixin`, `AttributeTrackSamplerMixin`). 이 처리는 렌더 스레드에서만 일어나며, 몹 생성·잠자기 같은 게임 규칙은 바닐라 시계를 따릅니다. 설정 `visualDay`로 끌 수 있습니다(기본 켬).

### 3.3 네트워크 패킷

| ID | 방향 | 필드 (코덱) | 용도 |
| --- | --- | --- | --- |
| `cheonsang:sky4` | 서버 → 클라이언트 | epochTick (VAR_LONG), epochJd (DOUBLE), rate (DOUBLE), lat (DOUBLE), lon (DOUBLE), city (STRING_UTF8) | 차원별 하늘 시계·압축률·월드 기본 위치·실시간 도시 |
| `cheonsang:placegen` | 서버 → 클라이언트 | gen (VAR_INT) | 월드 기본 위치 변경 번호. 받은 값이 이전과 다르면 클라이언트가 자기 `/cplace`를 해제 |
| `cheonsang:log_add` | 클라이언트 → 서버 | key (UTF-8 ≤96), name (UTF-8 ≤96), zoom (DOUBLE) | 관측 도감 등록, `adv:` 접두어는 발전과제 조건 부여 |
| `cheonsang:log_all` | 서버 → 클라이언트 | lines (UTF-8 ≤256 문자열 목록, 최대 20,000줄) | 플레이어 도감 전체 |
| `cheonsang:pose_set` | 클라이언트 → 서버 | on (BOOL) | 망원경 확대(×1 초과) 중인지 |
| `cheonsang:pose` | 서버 → 클라이언트 | id (UUID), on (BOOL) | 다른 플레이어의 망원경 자세 |

모든 패킷은 Fabric `PayloadTypeRegistry`로 등록되며, 송신 전 `ServerPlayNetworking.canSend`로 상대가 해당 채널을 받을 수 있는지 확인합니다(`placegen`, `pose`).

### 3.4 저장 데이터와 설정 파일

| 위치 | 형식 | 내용 |
| --- | --- | --- |
| 월드 저장 `cheonsang:sky` | `SavedData`, 문자열 → 실수 목록 | 차원 ID별 [기준 틱, 기준 율리우스일, 압축률, 위도] (+ 실시간 모드일 때 도시 번호와 켜기 전 값), `#lon` 키에 경도, `#gen` 키에 위치 변경 번호 |
| 월드 저장 `cheonsang:log` | `SavedData`, UUID → (항목 키 → "율리우스일\|배율\|이름") | 관측 도감. 한 사람 최대 15,000항목 |
| `config/cheonsang/server.json` | JSON | `rate`(1.0), `lat`(0), `lon`(0), `vanillaMoon`(true), `vanillaNight`(false) |
| `config/cheonsang/client.json` | JSON | `enabled`(true), `nakedEyeLimit`(6.0, 4\~6.5), `meteors`(1.0, 0\~5), `display`(설정 화면 값) |
| `config/cheonsang/places.json` | JSON | 개인 관측 위치, 서버·월드별 |
| `config/cheonsang/log_local.json` | JSON | 서버에 천상이 없을 때의 도감 |

### 3.5 확장팩 구조

확장팩 cheonsang-extra 1.0.1은 본체와 같은 리소스 이름공간(`assets/cheonsang/`)에 파일을 더하는 클라이언트 전용 jar입니다. 본체는 `assets/cheonsang/pack/<이름>.json`의 `format` 값을 5초마다 확인해 팩 상태(정상·없음·업데이트 필요)를 판정합니다. 팩 설명 파일은 `extra.json`(`{"format": 1, "pack": "extra", "version": "1.0.1"}`)과 `stars.json`(`{"format": 1, "pack": "stars", "version": "1.1.0", "bands": 180}`) 두 개입니다.

확장팩에는 Gaia 별 칸 파일, 맨눈 은하수 16K·32K 조각, 달 지도 8K가 들어 있습니다. 파일 목록과 크기는 5.5절에 정리했습니다. Gaia 별 칸 파일은 별 하나를 6바이트(칸 안 위치 16비트×2, 등급·색 8비트씩)로 줄이고 칸마다 zlib으로 압축합니다. 팩 칸에는 본체 별이 같은 값으로 들어 있어, 팩 칸으로 교체되어도 기존 별은 그대로 남고 어두운 별만 늘어납니다. 은하수 조각 한 장은 1024px에 가장자리 2px를 더한 1028×1028입니다.

---

## 4. 천문 계산 모델

### 4.1 시간척도

| 항목 | 사양 |
| --- | --- |
| 기본 단위 | 율리우스일(JD, UT). J2000.0 = JD 2451545.0 |
| 달력 변환 | Meeus 7장. 1582-10-15 이후 그레고리력, 그 전은 율리우스력. 연도 −2999\~2999 (천문 연도 표기) |
| 역학시 | TT = UT + ΔT. 해·달·행성 궤도는 TT 율리우스 세기로 계산 |
| ΔT | Espenak·Meeus(NASA) 구간 다항식: 1900\~1920, 1920\~1941, 1941\~1961, 1961\~1986, 1986\~2005, 2005\~2050, 2050\~2150. 범위 밖은 장기 포물선 ΔT = −20 + 32u², u = (y − 1820)/100. 2050\~2150 구간은 −20 + 32u² − 0.5628(2150 − y) |
| 항성시 | 그리니치 평균항성시 GMST (Meeus 12.4) |
| 하늘 시계 | JD = epochJd + (tick − epochTick) / 24000 × rate. 지방항성시 = 평균 태양 적경 + 마크 시각의 평균 태양 시간각 (압축률 1에서 GMST + 경도와 같음) |
| 게임 시각 | 관측 경도의 지방평균시. 0틱 = 06:00, 6000틱 = 정오 |
| 새 월드 기준 | 2026-08-11 06:00 (경도 0°, UT) = 월드 0틱, 위도 0°·경도 0°, 압축률 1.0 |
| 압축률 범위 | 0.01\~1000 (`/cele rate`) |

### 4.2 좌표계

| 좌표계 | 용도 |
| --- | --- |
| J2000 적도 (평균 분점) | 별·심원천체 카탈로그, 행성·위성 방향의 기준 좌표 |
| 날짜 적도 (평균 분점) | 해·달 위치, 관측 시점의 하늘 |
| J2000 황도 | 행성 해 중심 위치의 중간 좌표 (황도경사 23.43928°) |
| 지평 좌표 | 고도(지평선에서 위로)·방위각(북 0°, 동 90°) |
| 월드 축 | 동 = +X, 위 = +Y, 북 = −Z |

지평 변환은 지방항성시로 시간각 좌표를 만든 뒤 위도로 회전합니다(`Frames.horizon`). 정보 카드의 적경·적위는 관측 날짜 기준이며, 고도에는 대기 굴절이 포함됩니다.

### 4.3 세차와 황도경사

| 항목 | 사양 |
| --- | --- |
| 세차 | Meeus 21.2·21.3 (ζ, z, θ 3차 다항식). 행렬 R = Rz(z) · Ry(−θ) · Rz(ζ), J2000 적도 → 날짜 적도 |
| 황도경사 | 평균 황도경사 ε₀ (Meeus 22.2) |
| 장동 | 적용 안 함 |
| 광행차 | 해에만 적용 (−0.00569°). 별의 연주 광행차는 적용 안 함 |
| 고유운동 | 적용 안 함 (Gaia 위치는 J2016.0 → J2000.0으로 한 번 옮긴 값) |

별의 위치에는 J2000 좌표와 세차만 적용하며, Stellarium과의 차이는 약 1′입니다.

### 4.4 해

Meeus 25장 저정밀 식(평균 경도 L₀, 평균 근점이각 M, 이심률 e, 중심차 C, 반경 r = 1.000001018(1 − e²)/(1 + e cos ν))을 씁니다. 광행차 −0.00569°를 경도에 더하고 날짜의 평균 황도경사로 적도좌표로 바꿉니다. 겉보기 반지름은 해의 반지름 696,000 km로 구합니다.

### 4.5 달

| 항목 | 사양 |
| --- | --- |
| 위치 | Meeus 47장 전체 주기항: 경도·거리 60항(표 47.A), 위도 60항(표 47.B), 가산항 A₁·A₂·A₃, 이심률 보정 E |
| 위상 | Meeus 48장: 위상각, 밝은 비율, 밝은 쪽 위치각 χ |
| 칭동 | Meeus 53장 광학 칭동과 자전축 위치각 P. 물리 칭동·장동은 제외 (차이 0.04° 안팎) |
| 칭동 검증 | Meeus 예제 53.a (1992-04-12): l −1.20°, b +4.19°, P 15.07° (책: −1.206°, 4.194°, 15.08°) |
| 관측자 시차 | 지구 중심 방향에서 관측자 위치(지구 반지름 6378.14 km, 위도·지방항성시)를 뺀 방향 |
| 상수 | 달 반지름 1737.4 km |

### 4.6 행성

| 항목 | 사양 |
| --- | --- |
| 이론 | VSOP87D (Bretagnon & Francou 1988) 축약. 진폭 3×10⁻⁶ rad 이상 항만 남겨 1,259항. 계수는 PyMeeus가 수록한 VSOP87D 목록에서 읽음 |
| 대상 | 수성·금성·화성·목성·토성·천왕성·해왕성 + 지구 (지구 위치도 VSOP87) |
| 급수 | L·B·R = Σₖ τᵏ Σ A cos(B + Cτ), τ = TT 율리우스 천년 |
| 좌표 변환 | 해 중심 날짜 황도 → 날짜 적도 (평균 황도경사) → J2000 적도 (세차 행렬의 전치) → J2000 황도 |
| 지구 중심 방향 | 행성 해 중심 위치 − 지구 해 중심 위치, J2000 적도로 변환 |
| 광행시 | 2회 반복, 0.0057755일/AU |
| 겉보기 등급 | Meeus 41장 근사식. 토성은 고리 기울기 항 포함 |
| 겉보기 지름 | 1 AU 지름 (Meeus 55장) ÷ 지구 거리 |
| 토성 고리 | IAU 자전축 북극 (α 40.589°, δ 83.537°)으로 기울기 B와 위치각 P |
| 자전 | 화성 IAU 2009 모델, 목성 System II (IAU 극 + 870.27°/일). 대적점 System II 경도 = 2026-06-01에 91°, 한 달 1.75°씩 증가 (JUPOS 추세 직선 근사), 행면 위도 −22.5° |
| 계산 비용 | 행성 7개 1회 28µs |
| 렌더 갱신 | 실제 시간 1초 간격의 두 시점을 계산하고 그 사이 방향을 보간. 1초 동안 하늘 시간이 0.5일을 넘게 흐르면 매 프레임 계산 |

PyEphem 대조 최대 오차는 다음과 같습니다.

| 기간 | 수성 | 금성 | 화성 | 목성 | 토성 | 천왕성 | 해왕성 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1800\~2050년 | 4.3″ | 5.4″ | 12.6″ | 4.3″ | 3.8″ | 4.0″ | 2.4″ |

기원전 1000년\~서기 3000년 전체 범위의 최대 오차는 약 2′입니다. 같은 1800\~2050년 구간에서 이전 방식인 JPL 근사 궤도요소(Standish & Williams 1992)는 목성 7.9′, 토성 11.5′, 천왕성 17.1′의 오차를 보였습니다. 화성·목성 자전은 JPL Horizons와 대조해 화성 가운데 경도 1.27°(Horizons 1.28°), 목성 System III 45.41°(Horizons 45.41°)였습니다.

### 4.7 위성

| 대상 | 모델 | 정확도 |
| --- | --- | --- |
| 목성 갈릴레이 위성 4개 | Meeus 44장 저정밀식. 목성 적도면 좌표를 목성 자전축 위치각으로 하늘 위 동·북 오프셋으로 변환, cos u > 0이면 목성 앞 | 약 0.5 목성 반지름 |
| 토성 위성 7개 | JPL Horizons 1900\~2050 벡터에 맞춘 궤도식. 토성 적도면 위 λ = λ₀ + nt, 중심차·반경 주기항, 긴 주기 칭동항, 적도면 밖 높이항, 이아페투스 2λ 항 | 평균 오차: 타이탄 0.7″, 레아 0.3″, 미마스 0.66″, 엔셀라두스 0.07″, 테티스 0.52″, 디오네 0.02″, 이아페투스 1.02″. 최대 2.6″ |
| 토성 위성 등급 | 타이탄 8.3, 레아 9.7, 테티스 10.2, 디오네 10.4, 이아페투스 11.0, 엔셀라두스 11.7, 미마스 12.9 (충 무렵 평균) | |
| 가림 판정 | 위성이 모행성 뒤(시선 방향 뒤, 토성 반지름 60,268 km 안)면 숨김 | |

### 4.8 소행성

세레스·베스타는 JPL Horizons 해 중심 위치표(1900\~2050년, 4일 간격, `asteroids.bin`)를 4점 라그랑주 보간합니다. 표 범위 밖의 날짜에는 표시되지 않습니다.

### 4.9 일식·월식

| 항목 | 사양 |
| --- | --- |
| 화면 일식 | 별도 이벤트 없이 관측자 기준 달 방향과 겉보기 크기로 해를 가림 |
| 화면 월식 | Meeus 54장 그림자 반지름, Danjon 1.02 확대. 본영 = 1.02(0.998340π − s + πₛ), 반영 = 1.02(0.998340π + s + πₛ). π: 달 지평시차, s: 해 겉보기 반지름, πₛ = 8.794″/거리(AU) |
| 일식 예보 | 삭마다 그림자 축과 지구 타원체가 만나는 중심선을 1분 간격으로 계산, 중심선 점마다 관측자 시차로 개기·금환 판정 (도중에 바뀌면 혼성). 지속 시간 10초 간격. 탐색 범위 60년 |
| 월식 예보 | 보름마다 본영·반영 피복률로 개기·부분·반영 구분, P1·U1·U2·최대·U3·U4·P4 시각 |
| 도감 기록 기준 | 일식: 지평선 위 해 지름의 약 5% 초과 가림. 월식: 본영 2% 초과 (반영월식 제외) |

NASA 자료와 대조한 결과는 다음과 같습니다.

| 항목 | 천상 | NASA |
| --- | --- | --- |
| 2026-08-12 개기일식 최대 지점 | 65.1°N 25.1°W | 65.2°N 25.2°W |
| 같은 일식 개기 지속 · 띠 폭 | 2분 19초 · 301 km | 2분 18초 · 294 km |
| 2026-08-28 부분월식 본영식분 | 0.935 | 0.930 |
| 2028-12-31 개기월식 접촉 6개 | ±1분 이내 | |

### 4.10 대기 굴절

| 항목 | 사양 |
| --- | --- |
| 기하 → 겉보기 (그리기) | Sæmundsson: R(′) = 1.02 / tan(h + 10.3/(h + 5.11)), h ≥ −1.5°에서 적용 (h는 −0.9°에서 고정) |
| 겉보기 → 기하 (선택) | Bennett: R(′) = 1 / tan(h + 7.31/(h + 4.4)), h ≥ −1.0° |
| 대기 조건 | 1010 hPa, 10 ℃ 고정 |
| 적용 위치 | 별은 정점 셰이더(`stars.vsh`)에서 정점마다, 해·달·행성은 CPU에서 |

### 4.11 출몰

| 항목 | 사양 |
| --- | --- |
| 기준 고도 | 해 −0°50′, 별·행성·심원천체 −0°34′, 달 0.7275π − 34′ (π: 지평시차) |
| 좌표 | 해·달은 날짜 적도, 별·행성·심원천체는 J2000 + 세차 |
| 탐색 | 하루를 10분 간격으로 표본화 → 경계 구간 이분법 25회 → 남중은 최고 표본 주변 황금분할 30회 |
| 출력 | 출·남중(고도)·몰. 지지 않음·뜨지 않음·종일 위·종일 아래 구분 |

### 4.12 가시성 모델

한 함수가 관측 방향의 한계등급(limiting magnitude)을 계산하고, 별 표시·면광원 진하기·도감 판정이 모두 이 값을 씁니다. 적용 순서는 고정입니다.

| 단계 | 식·값 | 출처 |
| --- | --- | --- |
| ① 고유 밝기 | 점광원 등급 m, 면광원 표면 밝기 S (등/초각²) | 카탈로그 |
| ② 대기 소광 | Δm = k(X − 1), X = 1 / (sin h + 0.50572(h + 6.07995)^−1.6364), X ≤ 40. k: V 0.172, B 0.30, R 0.09 등/대기량 | Kasten & Young (1989) |
| ③ 하늘 밝기 | B = 자연 하늘(대기광, van Rhijn) + 박명(해 고도 표) + 달빛 + 빛공해 + 황도광·대일조. nL = 34.08 · exp(20.7233 − 0.92104 · mag) | Schaefer (1990), Krisciunas & Schaefer (1991) |
| 하늘 손실 | Δm_sky = 1.5 · log₁₀((1 + B/B₀)/2), B₀ = 22.0 등/초각². 보름밤 약 1.7등, 박명 −6° 약 5.4등 | |
| ④ 암순응 | 최대 3등 손실, 30초에 걸쳐 회복 | |
| ⑤ 장비 | 점광원 이득 (6.4절), 면광원 밝기 비 = (출사동/7mm)², 최대 1 | |
| ⑥ 판정 | 점광원: m + Δm ≤ 한계. 면광원: 표면 밝기 문턱 S_thr(a) = 26.5 − 2.5 log₁₀(1 + (15′/a)²)와 비교 | |

박명 천정 밝기 표(해 고도 → 등/초각²)는 −24° 30.0, −18° 24.0, −15° 21.0, −12° 18.2, −9° 15.2, −6° 12.2, −3° 9.5, 0° 7.3, 5° 5.5, 10° 4.6, 20° 3.9, 40° 3.4, 90° 3.0입니다. 하늘 밝기는 천정 한 점의 값을 하늘 전체에 적용합니다.

빛공해는 주변 ±64블록을 8블록 간격(17×17 = 289곳)으로 표본화해 하늘 빛 15인 지표면 칸의 블록 빛을 거리 가중으로 합산하고, Bortle 지수 = 1 + 8(1 − e^(−ΣL/800))로 환산합니다. Bortle 1\~9의 천정 밝기는 21.9, 21.6, 21.4, 20.8, 20.0, 19.1, 18.5, 18.0, 17.5 등/초각²입니다. 표본은 틱마다 40곳씩 나눠 읽고, 2초마다 또는 8블록 이상 이동했을 때 다시 계산합니다.

### 4.13 모델별 정확도 요약

| 모델 | 출처 | 정확도 (대조 기준) | 검증 범위 |
| --- | --- | --- | --- |
| ΔT | Espenak & Meeus (NASA) | 다항식 원본과 동일 | 1900\~2150 |
| 세차 | Meeus 21장 | 장동 미적용 | |
| 별 위치 | J2000 + 세차 | Stellarium 대비 약 1′ | |
| 해 | Meeus 25장 | 최대 43.7″ (PyEphem, 무작위 400시점) | 1900\~2100 |
| 달 | Meeus 47장 | 최대 30.0″, 거리 최대 9.8 km (PyEphem, 400시점) | 1900\~2100 |
| 달 칭동 | Meeus 53장 (광학) | 물리 칭동·장동 제외 0.04° 안팎 | |
| 행성 | VSOP87D 축약 1,259항 | 최대 2.4″\~12.6″ (PyEphem) | 1800\~2050 |
| 행성 (장기) | 같음 | 최대 약 2′ | 기원전 1000\~서기 3000 |
| 목성 위성 | Meeus 44장 | 약 0.5 목성 반지름 | |
| 토성 위성 | Horizons 맞춤식 | 평균 0.02″\~1.02″, 최대 2.6″ | 1900\~2050 |
| 세레스·베스타 | Horizons 위치표 보간 | 표 보간 | 1900\~2050 |
| 일식·월식 | 위 해·달 모델 | NASA 대조 (4.9절) | 1900\~2050 |
| 대기 굴절 | Sæmundsson, Bennett | 1010 hPa·10 ℃ 고정 | |

계산식 검증은 파이썬으로 재구현한 식과 원본 계수표를 PyEphem, Meeus 예제, NASA 일식·월식 자료와 비교하는 방식으로 했습니다. 일괄 검증 `verify_all.py`의 82개 검사 항목은 행성 계산을 VSOP87로 바꾸기 전에 모두 통과했으며, 바꾼 뒤의 행성 위치는 `vsop_check.py`로 따로 대조했습니다. 게임 화면의 직접 측정은 포함하지 않습니다.

---

## 5. 데이터 출처와 가공

### 5.1 측정 기준

본체 `cheonsang-2.0.0.jar`와 확장팩 `cheonsang-extra-1.0.1.jar`에 들어간 외부 데이터, 그리고 계산식의 계수로 쓰인 외부 자료를 대상으로 출처, 판본, 라이선스, 원본 규모, 가공 방법, 결과 규모, 검증 결과를 정리합니다. 수치의 근거는 다음과 같습니다.

- 결과 파일의 크기와 개수: 배포 jar를 풀어 직접 측정했습니다. 이진 파일의 개수는 파일 머리의 개수 필드를 읽었습니다. 파일 크기는 바이트 단위입니다.
- 원본 규모: `cheonsang/_data_raw/`의 파일을 직접 측정했습니다.
- 가공 방법: `cheonsang/_tools/*.py`의 설명문과 처리 코드에서 확인했습니다.
- 검증 결과: 검증 보고서(`_preview/verify_report.txt`, `천상열차_검증보고_1.9.0.md`, `_tools/korean_sky_report.md`)와 대조 스크립트 출력에 기록된 값입니다.

### 5.2 데이터셋 목록

| 분야 | 데이터셋 | 판본·출처 | 결과 파일 |
| --- | --- | --- | --- |
| 맨눈 별 | HYG Database | v4.1 (astronexus) | stars.bin, star_meta.bin, star_info.bin, star_names.json |
| 망원경 별 | Tycho-2 | CDS I/259 | deep_stars.bin |
| 망원경 별 보정, 성단·마젤란 별, 확장팩 별 | Gaia DR3 | gaia_source, tycho2tdsc_merge_best_neighbour, VizieR I/355 | deep_stars.bin, faint_stars.bin, 확장팩 cheonsang_stars/ |
| 근접 쌍성 | WDS | VizieR B/wds | stars.bin 등에 반성으로 병합 |
| 변광성 | GCVS | VizieR B/gcvs | 코드 내 광도곡선 매개변수 |
| 유명 별 위치 | HYG v4.1, SIMBAD | CDS | famous_stars.json |
| 서양 별자리 선·경계 | d3-celestial | Olaf Frohn | constellations.json, bounds.json |
| 서양 별자리 그림 | Stellarium "modern" | Johan Meuris 그림 | art_atlas.png, art.json |
| 천상열차분야지도 별자리 | Stellarium "chinese", Unicode Unihan | stellarium-skycultures | korean_constellations.json |
| 심원천체 | OpenNGC, 위키백과 | Mattia Verga | dso.json, dso_dist.json, dso_morph.json |
| 접안렌즈 스케치 | Digitized Sky Survey (DSS2 Red·Blue, DSS1 Red) | STScI/MAST, NASA SkyView | galaxy_atlas.png, galaxy_sketch.json |
| 구상성단 핵 반지름 | Harris 목록 | 1996, 2010년판 | 스케치 판 생성 매개변수 |
| 은하수 | NASA SVS Deep Star Maps 2020 | SVS #4851 | milkyway_real.png, milkyway_eye_12k.png, dso/mw_\*.png, 확장팩 tiles/eye16·eye32 |
| 달 지도 | NASA SVS CGI Moon Kit (LRO) | SVS #4720 | moon_map\*.png, 확장팩 moon_map_8k.png |
| 달 지명 | IAU WGPSN / USGS Gazetteer | 2026-11-11 내려받음 | moon_names.json |
| 행성 표면·고리 | Solar System Scope (INOVE) | 8k_mars, 8k_jupiter, 8k_saturn, 8k_saturn_ring_alpha | mars_map.png, jupiter.png, saturn.png, saturn_ring\*.png |
| 토성 위성, 소행성 | NASA/JPL Horizons | 1900\~2050 | 코드 내 궤도식 계수, asteroids.bin |
| 행성 위치 | VSOP87D | Bretagnon & Francou (1988), PyMeeus 수록 계수 | Vsop87Data.kt (생성 코드) |
| 달 위치 계수 | Meeus 47장 표 | astronomia moonposition.js | MoonTerms.kt (생성 코드) |
| 유성우 | IMO 유성우 일정 | International Meteor Organization | meteors.json |
| 목성 대적점 경도 | JUPOS | H.-J. Mettig 외 | 코드 내 추세 값 |
| 오로라 | 외부 데이터 없음 | 셰이더 모델 | aurora.vsh·aurora.fsh |

### 5.3 본체 천문 자료 파일 (`assets/cheonsang/sky/`)

| 파일 | 크기 (바이트) | 개수 | 내용 |
| --- | --- | --- | --- |
| `stars.bin` | 1,455,012 | 90,938 | 맨눈 별 (HYG v4.1 9.0등까지 83,479 + WDS 반성 7,459), 밝은 순 |
| `star_meta.bin` | 454,694 | 90,938 | 별자리 번호·그리스 문자·위첨자·플램스티드 번호 |
| `star_info.bin` | 1,304,349 | | 정보 카드용 거리·절대등급·HD 번호·분광형 |
| `star_names.json` | 7,832 | 188 | 밝은 별 한국어 이름 |
| `famous_stars.json` | 5,283 | 18 | 고유명 없는 유명 별 |
| `deep_stars.bin` | 29,076,868 | 2,409,260 | 망원경 별 (Tycho-2 + Gaia DR3 보정, 15.37등까지), 형식 `DSB2`, 1° 띠 180개·하늘 칸 41,252개 |
| `faint_stars.bin` | 2,461,140 | 153,821 | `deep_stars.bin`이 없을 때만 쓰는 9\~11등 대체 자료 |
| `constellations.json` | 22,382 | 89 | 서양 별자리 연결선 (88개 별자리, 뱀자리 머리·꼬리 분리) |
| `bounds.json` | 98,952 | 104 | 별자리 경계 선 묶음 |
| `art.json` | 15,228 | 85 | 별자리 그림 배치 |
| `korean_constellations.json` | 90,647 | 289 | 천상열차분야지도 별자리 |
| `dso.json` | 76,279 | 554 | 심원천체 위치·크기·방향·등급 (메시에 110, 칼드웰 109 포함) |
| `dso_dist.json` | 9,012 | 531 | 심원천체 거리 |
| `dso_morph.json` | 5,345 | 340 | 은하 형태 |
| `galaxy_sketch.json` | 10,279 | 462 | 스케치 판 칸 번호와 빛 면적 |
| `moon_names.json` | 39,513 | 787 | 달 지명 (한국어 이름 121곳) |
| `meteors.json` | 937 | 7 | 유성우 (산발 유성 시간당 10개) |
| `asteroids.bin` | 330,956 | 2 | 세레스·베스타 위치표 (4일 간격) |

### 5.4 본체 텍스처 (`assets/cheonsang/textures/sky/`)

| 파일 | 해상도 | 크기 (바이트) | 내용 |
| --- | --- | --- | --- |
| `milkyway_real.png` | 4096×2048 | 4,600,528 | 맨눈 은하수 |
| `milkyway_eye_12k.png` | 12288×6144 | 49,101,497 | 망원경용 은하수 |
| `dso/mw_lmc`·`mw_smc`·`mw_c99` | 1024×1024 | 648,823 · 424,887 · 1,042,279 | 대·소마젤란은하, C99 은하수 조각 |
| `dso/mw_m24` | 512×512 | 230,215 | M24 은하수 조각 |
| `galaxy_atlas.png` | 4096×2048 | 1,725,631 | 심원천체 스케치 판 (128×128 칸) |
| `art_atlas.png` | 4096×2816 | 1,851,167 | 별자리 그림 85장 |
| `moon_map_small.png` · `moon_map_soft.png` | 256×128 | 49,961 · 9,735 | 달 지도 (저배율) |
| `moon_map_1k.png` | 1024×512 | 710,851 | 달 지도 |
| `moon_map.png` | 2048×1024 | 2,830,157 | 달 지도 |
| `moon_map_4k.png` | 4096×2048 | 12,288,337 | 달 지도 |
| `phase_00`\~`phase_15.png` | 512×512 | 16장 | 달 위상 그림 (직접 생성) |
| `mars_map.png` | 2048×1024 | 2,888,551 | 화성 표면 |
| `jupiter.png` · `saturn.png` | 1024×1024 | 289,388 · 252,992 | 목성·토성 원판 |
| `saturn_ring.png` · `saturn_ring_front.png` | 2048×2048 | 4,301,582 · 3,944,601 | 토성 고리 |
| `grs.png` | 128×64 | 2,334 | 목성 대적점 |

### 5.5 확장팩 파일 (cheonsang-extra 1.0.1)

| 경로 (확장팩 jar 안) | 개수 | 크기 (바이트, 압축 전) | 내용 |
| --- | --- | --- | --- |
| `assets/cheonsang/pack/extra.json` | 1 | 50 | 팩 설명 (version 1.0.1) |
| `assets/cheonsang/pack/stars.json` | 1 | 64 | 별 팩 설명 (version 1.1.0, bands 180) |
| `cheonsang_stars/index.bin` | 1 | 371,280 | 하늘 칸 색인, 칸별 은하수 별빛 몫 |
| `cheonsang_stars/cNNNNN.bin` | 41,252 | 192,460,965 | 칸별 별 목록 (본체 별 + Gaia DR3 V ≤ 15.2) |
| `assets/cheonsang/textures/sky/tiles/eye16/` | 128 | 47,260,279 | 맨눈 은하수 16K, 16384×8192 (16×8 조각) |
| `assets/cheonsang/textures/sky/tiles/eye32/` | 512 | 160,749,343 | 맨눈 은하수 32K, 32768×16384 (32×16 조각) |
| `assets/cheonsang/textures/sky/moon_map_8k.png` | 1 | 49,113,719 | 달 지도 8192×4096 |
| `assets/cheonsang_extra/icon.png` | 1 | 2,199 | 확장팩 아이콘 128×128 |

jar 전체는 41,901개 항목(`fabric.mod.json`, `CREDITS.txt`, `LICENSE.txt`, `.mcmeta` 포함), 압축 전 449,961,204바이트입니다. Gaia 별은 32,061,510개(V 15.2등까지)입니다.

### 5.6 별 데이터

#### 5.6.1 HYG Database v4.1

- 출처: David Nash / astronexus, https://github.com/astronexus/HYG-Database. Hipparcos, Yale Bright Star, Gliese 목록을 통합한 별 목록입니다.
- 라이선스: CC BY-SA 4.0. 파생 파일도 CC BY-SA 4.0으로 공유합니다.
- 원본: `hygdata_v41.csv`, 33,932,548바이트, 119,626행.
- 가공: `build_data.py`가 9.0등까지 잘라 빅엔디언 이진 형식(별마다 적경·적위 float, 등급×100 short, B−V×1000 short, HIP int, 16바이트)으로 바꾸고 밝은 순으로 정렬합니다. `make_star_info.py`가 거리·절대등급·HD 번호·분광형을 같은 순서로 star_info.bin에 씁니다. star_meta.bin에는 별자리 번호, 바이어 문자, 위첨자, 플램스티드 번호가 들어갑니다. 한국어 별 이름은 본 프로젝트가 작성해 star_names.json에 넣었습니다.
- 결과: HYG 별 83,479개. WDS 반성을 더한 최종 stars.bin은 90,938개입니다(5.3절).
- 보정: 용골자리 에타(η Car)가 원자료에서 6.21등, 0.7′ 어긋난 위치로 들어 있어 불러올 때 4.3등과 목록 위치로 고칩니다.
- 검증: 게임 시각 18000·13000틱의 별 4개 고도·방위가 PyEphem과 0.1° 안에서 일치했습니다.

#### 5.6.2 Tycho-2

- 출처: Høg E. et al. (2000), A&A 355, L27. VizieR I/259 (doi:10.26093/cds/vizier.1259).
- 라이선스: CREDITS에 별도 이용 조건이 적혀 있지 않으며, 출처 표기와 VizieR 사용 고지를 따릅니다.
- 원본: `tycho2_full/tyc2.dat.*` 22개 파일(약 160MB, 2,539,913개 별).
- 가공: `make_deep_stars.py`가 처리합니다. 등급은 Tycho 변환식 V = VT − 0.090(BT−VT), B−V = 0.850(BT−VT)으로 구합니다. Gaia DR3 대응 별(대응표 2,502,118쌍)은 Gaia 등급·색·J2000 위치로 바꾸되, Gaia V와 Tycho V가 0.75등 넘게 다르면 Tycho 값을 유지합니다. HYG와 겹치는 별(V 9.8 이하, 5″ 안)은 뺍니다. 하늘을 적위 1° 띠 180개로 나누고 띠마다 적경 칸 수를 max(1, round(360·cos δ))로 정해 약 1°² 칸 41,252개를 만들고, 칸 안에서 밝은 순으로 정렬합니다.
- 결과: deep_stars.bin(형식 DSB2), 2,409,260개, 등급 상한 15.37등. 산개성단·마젤란은하의 Gaia 별을 포함하며 합성 별은 없습니다.

#### 5.6.3 Gaia DR3

- 출처: Gaia Collaboration, Vallenari et al. (2023), A&A 674, A1. ESA Gaia Archive, VizieR I/355/gaiadr3.
- 라이선스: CC BY-SA 3.0 IGO. 파생 파일도 CC BY-SA 3.0 IGO로 공유하며 ESA/DPAC 고지문을 CREDITS에 싣습니다.
- 쓰임과 원본:
  - 산개성단 52곳 둘레 별: `fetch_gaia_clusters.py`, G < 11.6, `gaia_open_clusters.tsv` 168,079바이트.
  - 대·소마젤란은하 별: `fetch_gaia_magellan.py`, G < 11.6, LMC 반지름 7°, SMC 3.75°, `gaia_magellan.tsv` 346,903바이트.
  - Tycho-2 대응표: `A2_tycho2_gaia_xmatch.csv`(gaiadr3.tycho2tdsc_merge_best_neighbour), 약 359MB.
  - 전천 별(확장팩): gaia_source G < 15.5, VOTable 압축본 `A1_*.vot.gz`, 53,860,707행. `gaia_vot_to_npy.py`로 npz 변환(약 1.7GB). `_data_raw/gaia` 전체 약 4.4GB.
  - HEALPix(레벨 5) 칸별 별빛 합계: `A3_healpix5_gbin_flux.csv` 약 14MB.
- 가공: `gaia_conv.py`에 공통 변환식을 둡니다. G→V는 Riello et al. (2021), A&A 649, A3의 3차 다항식 G−V = −0.02704 + 0.01424x − 0.2156x² + 0.01426x³ (x = BP−RP, −0.5\~5.0 밖은 경계값)입니다. BP−RP→B−V는 Tycho-2 대응 별에서 0.1 간격 중앙값으로 만든 표(`bv_table.json`)를 씁니다. 위치는 J2016.0에서 선형 고유운동으로 J2000.0으로 옮깁니다. BP−RP가 없으면 V = G + 0.1, B−V = 0.65로 둡니다.
- 확장팩 가공(`make_gaia_pack.py`, `make_extra_pack.py`): 9.0 < V ≤ 15.2 별만 남기고, HYG 별(V 9.8 이하, 5″ 안)과 본체 별(2″ 안, 등급차 0.75 미만)을 뺍니다. 칸은 본체와 같은 41,252칸이며 각 칸에 본체 별과 남은 Gaia 별을 밝은 순으로 넣습니다. 별 하나는 6바이트(칸 안 위치 16비트×2, (V−6)×25 8비트, (B−V+0.5)×80 8비트)이고 칸마다 zlib으로 압축합니다. index.bin에는 칸별 은하수 별빛 몫(한계 V 11.5\~15.5, 0.5등 간격)을 둡니다.
- 결과: faint_stars.bin 153,821개 중 Gaia 별은 산개성단 별 1,609개와 마젤란은하 별 약 3,150개이고 나머지는 합성 배경 별입니다. deep_stars.bin이 있으면 이 파일은 읽지 않으며, 성단·마젤란 Gaia 별은 deep_stars.bin에도 들어 있습니다. 확장팩 cheonsang_stars/는 41,253개 파일(칸 41,252 + index.bin), 192,832,245바이트, Gaia 별 32,061,510개입니다.
- 검증: G→V 변환 잔차(Tycho V < 10.5, 색 구간 13개)는 구간 중앙 −0.009\~+0.031등, MAD 0.014\~0.052등입니다. Gaia와 Tycho-2의 J2000 위치 차는 중앙값 0.08″, 99% 0.58″입니다. 팩 칸 디코드 검사를 통과했습니다. 팩이 있을 때 은하수에서 빼는 별빛 몫은 평균 한계 13등 31%, 15등 67%입니다.

#### 5.6.4 WDS 근접 쌍성

- 출처: Mason B.D. et al. (2001), AJ 122, 3466, The Washington Visual Double Star Catalog. VizieR B/wds.
- 라이선스: CREDITS에 별도 이용 조건이 적혀 있지 않으며 출처 표기를 따릅니다.
- 원본: `wds/wds.tsv`, 약 24MB, 158,047행(주석 포함).
- 가공(`make_wds.py`): 성분 표시가 없거나 대문자 두 글자(AB, CD 등)이거나 `A,BC` 꼴인 줄 가운데 주성 9.5등 이하, 반성 13등 이하, 최근 관측 분리각 0.5\~30″인 것만 씁니다. HYG 별과의 대응 조건은 WDS 위치 15″(고유운동×30년 추가) 안, 주성 등급 또는 합성 등급과 1등 안입니다. HYG가 합성 빛이면 성분 등급으로 나눕니다. 반성 위치는 주성 위치에 위치각·분리각을 적용해 구면 삼각법으로 구합니다. 반성이 HYG(분리각 + 8″ 안) 또는 deep_stars(2.5″ 안)에 이미 있으면 넣지 않습니다. 이미 있는 HYG 반성의 위치가 WDS 최근값과 1″ 넘게 다르면 WDS 위치로 옮깁니다. 원본 별 목록은 `_data_raw/stars_base_1.7/`에 보관하고 매번 그 원본에서 다시 만듭니다.
- 결과: stars.bin의 추가 반성 7,459개(HIP 0인 별 수와 전체 별 수 차이로 측정). 위치를 옮긴 HYG 반성 105개.
- 검증: 카스토르 1.93 + 2.85등, 포리마 3.47 + 3.52등, 미자르 2.23 + 3.95등, 알마크 2.19 + 4.9등으로 중복 없이 들어갑니다. 분리각은 시리우스 B 11.1″, 카스토르 5.4″, 포리마 3.4″, 미자르 14.4″, 아크룩스 3.5″, 리길 켄타우루스 8.1″로 WDS 최근값과 일치합니다.

#### 5.6.5 GCVS 변광성, SIMBAD

- GCVS: Samus et al. (2017), Astronomy Reports 61, 80, VizieR B/gcvs. 알골(주기 2.8673일, 2.1\~3.4등)과 미라(3.4\~9.3등)의 광도곡선 매개변수로만 쓰며, 파일로 묶은 자료는 없습니다.
- 유명 별 18개(famous_stars.json): 위치·등급·거리는 HYG v4.1입니다. HYG에 없는 타비의 별(KIC 8462852)과 방패자리 UY는 SIMBAD ICRS 좌표를 씁니다. 한 줄 설명(한국어·영어)은 본 프로젝트가 작성했으며 CC BY-SA 4.0입니다.

### 5.7 별자리 데이터

#### 5.7.1 d3-celestial 별자리선·경계

- 출처: Olaf Frohn, https://github.com/ofrohn/d3-celestial. BSD 3-Clause License(전문을 CREDITS 끝에 수록).
- 원본: `constellations.json` 50,581바이트, `constellations.lines.json` 27,136바이트, `constellations.bounds.json` 40,714바이트.
- 가공: `build_data.py`가 선과 이름 위치를 constellations.json으로 만들고 한국어 이름을 붙입니다. `make_bounds.py`는 이웃 별자리가 같이 쓰는 변을 한 번만 남기고, 적경선·적위선을 따라가도록 경계를 1° 간격으로 나눕니다.
- 결과: constellations.json 89개 항목(88개 별자리, 뱀자리 머리·꼬리 분리), bounds.json 104개 선 묶음.

#### 5.7.2 Stellarium "modern" 별자리 그림

- 출처: Stellarium sky culture "modern", 그림 Johan Meuris. 그림은 Free Art License 1.3, 데이터는 CC BY-SA 4.0입니다.
- 원본: `stellarium_art/`(약 2.4MB).
- 가공: `make_art.py`가 85장을 가로 4096px 한 장에 모으고, 그림마다 맞출 별 3개(HYG 좌표)를 art.json에 기록합니다. 그림 자체는 바꾸지 않았습니다.
- 결과: art_atlas.png 4096×2816, art.json 85개 항목.

#### 5.7.3 천상열차분야지도 별자리

- 출처: Stellarium sky culture "chinese"(전통 중국 별자리, "Text and line: CC BY-SA", 버전 표기 없음). 한자 읽기 검증에 Unicode Unihan Database(Unihan_Readings, Unihan_Variants, Unicode License)를 썼으며, Unihan 자료는 배포본에 넣지 않았습니다.
- 원본: `stellarium_chinese_index.json` 240,478바이트(별자리 318개), `unihan/` 약 9.2MB, 본 프로젝트가 정리한 보천가 체계 목록 `cheonsang_list_20261114.md`(302좌 항목).
- 가공: `make_korean_sky2.py`가 목록에 있는 별자리만 골라 한자 이름(간체·번체 변환 후 비교)으로 Stellarium 선과 맞춥니다. 한글 이름은 Unihan kHangul 한자음과 대조하고, 다르면 한자음을 따릅니다(두음법칙 차이만 있으면 목록 표기). 별 위치는 HIP 번호로 HYG v4.1 J2000 좌표를 씁니다. 영어 화면용 로마자 이름은 국어의 로마자 표기법(연음·비음화·유음화 반영)으로 붙였습니다.
- 결과: korean_constellations.json 289개 별자리. 보천가 283좌에 천창(天槍)·태존(太尊)·토공리(土公吏)를 더했습니다. 명나라 때 더해진 남극 부근 별자리 23개와 현대 별 대응 자료가 없는 5개(천직·천묘·동구·군문·기부)는 제외했습니다.
- 검증(`korean_sky_report.md`, `천상열차_검증보고_1.9.0.md`): 이름 7개를 고쳤습니다(천구→천주, 환관→환자, 철질→부질, 추호→추고, 적시기→적시, 天紀→天記, 주귀→주기). 목록과 별 수가 3개 넘게 다른 별자리는 6개(구감, 십이국, 외주, 어녀, 익, 진)입니다. HYG에 없는 HIP는 3개(55203, 78727, 115125)입니다. 중복 기재 항목은 한 번만 넣었습니다.

### 5.8 심원천체 데이터

#### 5.8.1 OpenNGC, 메시에·칼드웰

- 출처: OpenNGC, Mattia Verga, CC BY-SA 4.0. 칼드웰 번호, 거리, 은하 형태 일부는 위키백과("Messier object", "Caldwell catalogue", 개별 은하 문서), CC BY-SA 4.0입니다.
- 원본: `NGC.csv` 3,942,946바이트(13,970행), `addendum.csv` 17,496바이트, `messier_wiki.txt` 63,352바이트, `caldwell_wiki.txt` 34,646바이트.
- 가공:
  - `build_data.py`: 메시에 110, 칼드웰 109, 대·소마젤란 2. M102 = NGC 5866으로 두고 C9·C41·C99는 수동 입력합니다. 위키 문법 찌꺼기를 제거합니다.
  - `make_dso_dist.py`: 위키백과 표의 거리. `make_galaxy_types.py`: 은하 드보클레르 형태 77개.
  - `add_magellan_dso.py`: 대마젤란 중심 6°, 소마젤란 3° 안, V(없으면 B) 11.0 이하 성운·성단 69개.
  - `add_galaxies_110.py`: OpenNGC Type G 중 V(없으면 B) 11.0 이하 은하 264개. 허블 형태는 OpenNGC 값을 쓰고, 시선속도 500 km/s 초과는 v/70 Mpc로 거리를 구합니다.
- 결과: dso.json 554개. 메시에 110, 칼드웰 109를 포함하며 종류별로 은하 341, 산개성단 83, 구상성단 67, 발광·반사성운 43, 행성상성운 17, 기타 3입니다. dso_dist.json 531개, dso_morph.json 340개.

#### 5.8.2 DSS 접안렌즈 스케치 판

- 출처: Digitized Sky Surveys, STScI (NASA grant NAG W-2166). POSS-II(Caltech), POSS-I(National Geographic Society–Palomar), UK Schmidt(AAO) 건판. MAST를 거쳐 NASA SkyView로 받았습니다.
- 라이선스: CC BY-NC-SA 4.0 (https://archive.stsci.edu/dss/copyright.html). 영리 이용이 금지되며, 파생물인 galaxy_atlas.png도 CC BY-NC-SA 4.0입니다.
- 원본: `dss_galaxies/` FITS 466개(약 73MB, `fetch_galaxy_dss.py`, 긴 지름×1.9 정사각형, 192px). 성운용 추가 건판 `neb_extra/`(약 55MB, `fetch_neb_extra.py`, 긴 지름×3.8, DSS2 Red·DSS2 Blue·DSS1 Red).
- 가공(`make_galaxy_atlas.py`): 천체마다 128×128 칸을 씁니다. 건판 농도 D를 I = 10^((D−배경)/γ) − 1, γ = (가장 밝은 곳 − 배경)/1.3으로 밝기로 바꾸고 로그 값으로 저장합니다. 별 알갱이(7px 중앙값보다 5σ 밝은 점)를 지우고 잡음 3σ 아래를 0으로 둡니다. 성운은 DSS2 Blue 0.65 + Red 0.35로 합성하고, Blue와 Red의 모양 상관이 0.5 미만이면 DSS1 Red를 반반 섞습니다. 포화된 밝은 HII 영역(M42, M43, M8, M17, M20, C103, C92)은 가운데 밝기를 깊이로 복원합니다. 구상성단은 King 분포(조석 반지름 1.1R, 지수 1.3)로 매끈한 원반을 만들고, 13개 성단의 핵 반지름은 Harris (1996, 2010년판) 측정값(0.035\~0.3R), 나머지는 0.12R로 둡니다. M57은 건판 포화 때문에 고리 식(구멍 35%, 고리 0.62R)으로 그립니다.
- 결과: galaxy_atlas.png 4096×2048 흑백, 462칸(은하 339, 성운·행성상성운·구상성단 123). 사진이 없는 은하 2개(대·소마젤란)는 형태 모델로 그립니다.
- 검증: 32의 배수 칸에서 셰이더가 칸 번호를 보간해 64를 63.999로 읽으면 가로 줄무늬가 생기므로, 칸 번호를 정수로 반올림해 읽습니다.

### 5.9 은하수 데이터

- 출처: NASA/Goddard Space Flight Center Scientific Visualization Studio, Deep Star Maps 2020 (https://svs.gsfc.nasa.gov/4851). Hipparcos-2, Tycho-2, Gaia DR2 별 약 17억 개로 계산한 전천 지도입니다. NASA 이미지로 미국 내 저작권이 없습니다.
- 원본: 선형 EXR `milkyway_2020_4k.exr` 36,436,668바이트, `milkyway_2020_8k.exr` 137,307,727바이트, `milkyway_2020_32k.exr` 1,509,917,679바이트.
- 가공 스크립트: `make_milkyway.py`(4K), `make_milkyway_real.py`(맨눈용 색 제거), `make_milkyway_8k.py`(8K), `make_base_160.py`(기본 화질), `make_mw_12k.py`(12K), `make_mw_patches.py`(대·소마젤란, M24, C99 조각), `make_ultra_161.py`(16K·32K 조각). 공통 톤매핑은 바닥 35% 빼기, 99.8% 값을 1로, 감마 1/1.6입니다. 맨눈용은 채도 20%, 큰 흐림 대비 절반, 회색입니다. 32K 조각은 천상이 그리는 별(HYG 9.5등까지) 자리에 남은 점을 주변 중앙값으로 메우고, 조각마다 사방 2px 여백을 둡니다. 모든 해상도가 8K 원본에서 구한 바닥·상한 값을 같이 써서 확대 단계가 바뀌어도 밝기가 이어집니다.
- 결과: 본체 텍스처는 5.4절, 확장팩 조각은 5.5절에 정리했습니다.
- 별빛 중복 처리: 확장팩이 있으면 새로 그리는 Gaia 별의 빛을 HEALPix 칸별로 계산해 은하수 영상에서 뺍니다(6.2절).

### 5.10 달 데이터

#### 5.10.1 LROC 텍스처

- 출처: NASA's Scientific Visualization Studio, CGI Moon Kit (LRO 데이터), https://svs.gsfc.nasa.gov/4720. NASA 이미지로 미국 내 저작권이 없습니다.
- 원본: `lroc_color_2k.jpg` 457,942바이트, `lroc_color_poles_4k.tif` 13,095,388바이트, `lroc_color_poles_8k.tif` 50,641,970바이트.
- 가공: `make_moon_map.py`(2048×1024), `make_base_160.py`(4K 원본에서 4096·2048·1024·256px 단계), `make_ultra_161.py`·`make_extra_pack.py`(8K).
- 결과: 본체 moon_map 4단계와 저배율 지도(5.4절), 확장팩 moon_map_8k.png(5.5절).

#### 5.10.2 달 지명

- 출처: Gazetteer of Planetary Nomenclature, IAU WGPSN / USGS Astrogeology Science Center. 미국 정부 저작물로 공공 영역입니다.
- 원본: `MOON_nomenclature_center_pts.zip` 23,845,070바이트(2026-11-11 내려받음).
- 가공(`make_moon_names.py`): 앞면과 칭동으로 보이는 가장자리(경도 ±100°) 안에서 바다·대양·호수·만·늪 전부, 지름 20 km 이상 크레이터(위성 크레이터 제외), 지름 50 km 이상 산·산맥·계곡·절벽·곶을 고릅니다. 한국어 이름은 본 프로젝트가 작성했습니다.
- 결과: moon_names.json 787곳(한국어 이름 121곳).

### 5.11 행성·작은 천체 데이터

#### 5.11.1 행성 텍스처

- 출처: Solar System Scope (INOVE), https://www.solarsystemscope.com/textures/, CC BY 4.0(NASA 자료 바탕).
- 원본: `8k_mars.jpg` 8,396,951바이트, `8k_jupiter.jpg` 3,080,980바이트, `8k_saturn.jpg` 1,098,204바이트, `8k_saturn_ring_alpha.png` 64,790바이트.
- 가공(`make_base_160.py`): 화성은 2048×1024로 축소합니다. 목성·토성은 경도 방향 평균으로 위도별 띠 색만 남기고 대비·채도를 낮춰 원판으로 그립니다(대적점은 따로 그 시각 경도에 그립니다). 토성 고리는 실제 방사 단면(C·B·카시니 간극·A·엥케 간극)을 토성 반지름 단위로 맞춰 정면 모습으로 그립니다.
- 결과: mars_map.png, jupiter.png, saturn.png, saturn_ring.png, saturn_ring_front.png(5.4절). 위상 그림 phase_00\~15는 `make_planet_textures.py`로 직접 생성했으며 외부 자료를 쓰지 않습니다.

#### 5.11.2 JPL Horizons

- 출처: NASA/JPL Horizons System, https://ssd.jpl.nasa.gov/horizons/. 미국 정부 자료입니다.
- 원본(`fetch_horizons.py`): 토성 위성 7개(타이탄 606, 레아 605, 미마스 601, 엔셀라두스 602, 테티스 603, 디오네 604, 이아페투스 608), 토성 중심 ICRF 적도, 1900\~2050 하루 간격, 파일당 약 4.77MB. 세레스(1)·베스타(4), 해 중심 J2000 황도, 1900\~2050 4일 간격, 파일당 약 1.19MB.
- 가공: `fit_saturn_moons.py`(타이탄·레아), `fit_saturn_moons2.py`(나머지 5개)가 평균 경도·이심 항·공명 칭동·적도면 밖 높이를 가진 궤도식에 맞추고, 맞춘 계수만 코드에 넣습니다. `make_asteroids.py`가 세레스·베스타 위치표를 asteroids.bin에 씁니다.
- 결과: asteroids.bin(천체 2개, 4일 간격). 토성 위성 원자료는 배포본에 들어가지 않습니다.
- 검증: 타이탄 평균 0.73″·최대 1.53″, 레아 평균 0.29″·최대 0.50″(`saturn_moons_fit.txt`). 미마스 0.66″, 엔셀라두스 0.07″, 테티스 0.52″, 디오네 0.02″, 이아페투스 1.02″(평균, 최대 2.6″). 화성 가운데 경도와 목성 System III 대조 결과는 4.6절에 있습니다.

#### 5.11.3 VSOP87

- 출처: Bretagnon P., Francou G. (1988), A&A 202, 309, IMCCE. 계수는 PyMeeus 패키지에 수록된 VSOP87D 목록에서 읽었습니다. CREDITS에서는 METHODS 절에 분류합니다.
- 원본: PyMeeus의 VSOP87D 전체 계수(수성\~해왕성 및 지구).
- 가공(`make_vsop87.py`): 진폭 3×10⁻⁶ rad 이상인 항만 남겨 Vsop87Data.kt를 생성합니다. 날짜 황도·춘분점 기준 결과를 J2000 황도로 바꾸고 광행시 보정을 적용합니다. 지구 위치도 VSOP87로 구합니다.
- 결과: 1,259항, Vsop87Data.kt 46,744바이트. 행성 7개 계산 1회 28µs.
- 검증(`vsop_check.py`, PyEphem 대조): 1800\~2050년 최대 오차는 4.6절 표와 같습니다. 기원전 1000년\~서기 3000년 전체 최대 약 2′입니다.

#### 5.11.4 달 위치 계수

- 출처: J. Meeus, Astronomical Algorithms 2nd ed. 47장 표 47.A·47.B. 계수는 astronomia 라이브러리의 `moonposition.js`(MIT, 8,325바이트)에서 읽었습니다.
- 가공: `gen_moon_terms.py`가 MoonTerms.kt(경도·거리 60항, 위도 60항)를 생성하고, `astro_check.py`가 같은 표를 직접 읽어 검증합니다.
- 검증: PyEphem 400시점 대조에서 달 최대 30.0″, 거리 최대 9.8 km입니다.

### 5.12 유성우·오로라·계산 문헌

- IMO 유성우: International Meteor Organization 일정의 극대 황경, 복사점, ZHR, 속도(사실 자료)를 씁니다. `build_data.py`가 meteors.json을 만듭니다. 유성우 7개(사분의자리, 거문고자리, 물병자리 에타, 페르세우스자리, 오리온자리, 사자자리, 쌍둥이자리)와 산발 유성 시간당 10개입니다.
- 오로라: 외부 데이터를 쓰지 않습니다. 발생 위도(|φ| > 60°, 67\~75°에서 최대), 고도 80\~320 km 층, 557.7 nm·630 nm 발광 색을 셰이더 모델로 계산하고, 그날의 활동 세기는 난수로 정합니다.
- JUPOS: 목성 대적점 경도 추세(사실 값만)를 씁니다.
- 계산 방법 문헌(데이터 복사 없음): Meeus, Krisciunas & Schaefer (1991), Schaefer (1990), Kasten & Young (1989), Sæmundsson (1986), Bennett (1982), IAU WGCCRE (Archinal et al. 2011/2018), Espenak·Meeus ΔT 다항식.

### 5.13 품질 검증 요약

| 항목 | 방법 | 결과 |
| --- | --- | --- |
| 천문 계산 일괄 검증 | `verify_all.py` (astro_check 41, planet_check 24, eclipse_check 11, eclipse_lat_check 6) | 82항목 통과 (행성 VSOP87 전환 전 실행) |
| 행성 위치 | `vsop_check.py`, PyEphem 1800\~2050 | 최대 2.4″\~12.6″ |
| 해·달 위치 | `astro_check.py`, PyEphem 400시점 (1900\~2100) | 해 최대 43.7″, 달 최대 30.0″, 거리 최대 9.8 km |
| G→V 변환 | Tycho-2 대응 별 비교 | 구간 중앙 −0.009\~+0.031등 |
| Gaia–Tycho 위치 | J2000 위치 차 | 중앙 0.08″, 99% 0.58″ |
| WDS 쌍성 | 대표 쌍성 분리각을 WDS 최근값과 비교 | 일치 (시리우스 B 11.1″ 등) |
| 토성 위성 궤도식 | Horizons 1900\~2050 대조 | 평균 0.02″\~1.02″, 최대 2.6″ |
| 일식·월식 | NASA 자료 대조 | 2026-08-12 개기일식 최대 지점 0.1° 안, 2028-12-31 개기월식 접촉 6개 ±1분 |
| 천상열차분야지도 | 목록·Stellarium·Unihan 3자 대조 | 289좌, 이름 7개 수정, 별 수 차이 6개 기록 |
| 라이선스 | CREDITS.txt와 원 라이선스 페이지 대조 | DSS 비상업 조건 확인 |

알려진 데이터 한계는 다음과 같습니다. 별은 J2000 좌표에 세차만 적용하고 장동·광행차·고유운동에 의한 위치 변화를 넣지 않아 Stellarium 대비 약 1′ 차이가 납니다. 달은 물리 칭동과 장동을 제외해 0.04° 안팎의 오차가 있습니다. 세레스·베스타와 토성 위성은 1900\~2050년 자료에 맞춘 것입니다. faint_stars.bin은 본체에 들어 있으나 deep_stars.bin이 있어 읽히지 않습니다.

### 5.14 라이선스 요약

| 자료 | 라이선스 | 상업 이용 | 조건 |
| --- | --- | --- | --- |
| 모드 소스 코드 | MIT | 가능 | 저작권 고지 |
| 원작 텍스처·아이콘, 한국어 이름 | CC BY-SA 4.0 | 가능 | 출처 표기, 동일조건 |
| HYG v4.1 및 파생 별 파일 | CC BY-SA 4.0 | 가능 | 출처 표기, 동일조건 |
| Gaia DR3 및 파생 파일, 확장팩 별 | CC BY-SA 3.0 IGO | 가능 | ESA/DPAC 고지, 동일조건 |
| Tycho-2, WDS, GCVS (VizieR) | CREDITS에 명시 없음 | 미기재 | 출처·VizieR 고지 |
| SIMBAD 좌표 2개 | CREDITS에 명시 없음 | 미기재 | SIMBAD 고지 |
| d3-celestial | BSD 3-Clause | 가능 | 저작권 고지, 라이선스 전문 |
| Stellarium "modern" 그림 | Free Art License 1.3 | 가능 | 동일 라이선스 |
| Stellarium "chinese" 선·이름 | CC BY-SA (버전 미표기) | 가능 | 동일조건 (파생 파일 CC BY-SA 4.0) |
| Unicode Unihan | Unicode License | 해당 없음 | 검증에만 사용, 미포함 |
| OpenNGC | CC BY-SA 4.0 | 가능 | 출처 표기, 동일조건 |
| 위키백과 (칼드웰 번호, 거리, 형태) | CC BY-SA 4.0 | 가능 | 출처 표기, 동일조건 |
| DSS 스케치 판 (galaxy_atlas.png) | CC BY-NC-SA 4.0 | 불가 | 비상업, 출처 표기, 동일조건 |
| Harris 구상성단 목록 | 측정값 인용 | 가능 | 문헌 인용 |
| NASA SVS 은하수·달 지도 | NASA 미디어 지침 (미국 내 저작권 없음) | 가능 | 출처 표기 |
| IAU WGPSN / USGS 달 지명 | 공공 영역 | 가능 | 없음 |
| Solar System Scope 텍스처 | CC BY 4.0 | 가능 | 출처 표기 |
| JPL Horizons | 미국 정부 자료 | 가능 | 없음 |
| IMO 유성우, JUPOS | 사실 자료 | 가능 | 출처 표기 |
| astronomia moonposition.js (달 계수) | MIT | 가능 | 저작권 고지 |
| VSOP87D 계수 (PyMeeus 수록) | CREDITS METHODS 절 (방법으로 분류) | 미기재 | 문헌 인용 |

배포 jar는 집합물이며 각 파일이 원 자료의 라이선스를 따릅니다. 동일조건 라이선스는 해당 자료에서 만든 파일에만 적용됩니다. 본체에는 비상업 조건 파일(galaxy_atlas.png)이 하나 들어 있으므로 본체 jar 전체를 상업적으로 재배포할 수 없습니다. 확장팩에는 비상업 조건 파일이 없습니다. CREDITS.txt 첫머리의 라이선스 요약(LICENSING AT A GLANCE)에 DSS 스케치 판의 CC BY-NC-SA 4.0 조건이 별도 항목으로 적혀 있습니다.

---

## 6. 렌더링

### 6.1 별

별은 정점 4개짜리 사각형으로 그리고 조각 셰이더에서 가우스 점으로 칠합니다(`stars.vsh`·`stars.fsh`). 정점 셰이더가 별마다 다음을 계산합니다.

* 굴절: 4.10절 Sæmundsson 식으로 정점을 천정 쪽으로 옮김
* 소광: 대기량 X로 m ← m + k(X − 1)
* 투명도: α = clamp((한계 + 0.5 − m)/5, 0, 1)^1.4
* 크기: 원하는 크기 0.22 · 10^(−0.1(m − 한계))를 화면 픽셀로 환산, 최소 반지름 1.5px. 최소 반지름보다 작아진 만큼 진하기로 보상해 밝기 총량을 유지
* 망원경 별 상: 최소 반지름에 회절·시상 크기(6.4절)를 반영
* 지평선 색: R 0.09·V 0.172·B 0.30 등/대기량 비로 붉어짐, 대기량 10에서 고정
* 반짝임: 대기량 3에서 고정, 지평선 쪽 최대 ±15%, 고배율에서 0
* 달 가림: 달 원판 안의 별은 그리지 않음

별 색은 B−V를 Ballesteros(2012) 식으로 유효온도로 바꾼 흑체 색이며, 한계등급보다 많이 밝은 별일수록 채도가 높아집니다(최대 0.55).

망원경 별은 시야 근처 하늘 칸에서 밝은 순으로 최대 150,000개를 골라 버퍼로 만듭니다(`DeepStars`). Gaia 칸은 배경 스레드 하나가 읽으며, 아직 읽히지 않은 칸은 본체 칸으로 대신 그립니다.

### 6.2 은하수

| 항목 | 사양 |
| --- | --- |
| 원본 | NASA SVS Deep Star Maps 2020 (Hipparcos-2·Tycho-2·Gaia DR2 별 약 17억 개, 선형 EXR) |
| 본체 | 맨눈 4096×2048, 망원경 12288×6144. 흑백은 R8 단일 채널 텍스처 |
| 확장팩 | 16K(16×8)·32K(32×16) 조각, 조각당 1024px + 가장자리 2px |
| 조각 스트리밍 | 3스레드 풀에서 읽고, 프레임당 최대 4장 업로드, 대기 최대 12장, 0.3초 교차 전환. 예산을 넘으면 오래 보지 않은 조각부터 내림 |
| 보간 | 바이큐빅(Mitchell) + 디더링 셰이더 (`milky.fsh`) |
| 확장팩 별빛 차감 | Gaia HEALPix 12,288칸 × 0.5등 간격 별빛 합계로 칸마다 "이미 별로 그린 빛"의 몫을 계산해 은하수 밝기에서 뺌. 몫 평균: 한계 13등 31%, 15등 67% |
| 배율 감쇄 | (출사동 비)^0.33 × (1 − 별로 분해된 몫), 최소 0.10 |

### 6.3 해·달·행성

* 해: 겉보기 반지름 그대로, 굴절에 의한 상하 압축. 개기일식 때 코로나 영역 표시(`sun.fsh`).
* 달: 위상·지구조·광학 칭동을 셰이더에서 적용(`moon.fsh`). 확대 단계에 따라 256 → 1K → 2K → 4K → 8K(확장팩) 지도.
* 화성: 2048×1024 표면 지도를 IAU 자전 모델로 회전.
* 목성: 줄무늬 원판 + 대적점(System II 경도).
* 토성: 원판 + 고리 앞·뒤 두 장, 기울기 B로 타원 변형.
* 작게 보이는 행성·위성·소행성: 별과 같은 셰이더의 점으로 그림.

### 6.4 망원경 광학 모델

| 항목 | 값·식 |
| --- | --- |
| 구경 D | 50, 60, 70, 80, 90, 102, 114, 127, 150, 200, 254, 305mm (12단계, 기본 80mm) |
| 접안 f | 40, 32, 25, 20, 15, 12, 9, 7, 5, 4, 3mm (11단계) |
| 초점비 | f/7 고정, 대물 초점거리 F = 7D |
| 배율 | M = F / f, 최대 ×712 (305mm·3mm) |
| 유효 구경 | D_eff = min(D, 7mm × M) |
| 점광원 이득 | Δm = 5 log₁₀(D_eff / 7mm) + 0.43 (맨눈 6.5등에서 7.7 + 5 log₁₀(D/cm)와 같음) |
| 한계등급 (맨눈 6.0 기준) | 80mm 11.7등, 305mm 14.6등 |
| 한계등급 상한 | 본체 13.0등, 확장팩 15.2등 |
| 출사동 | D / M, 동공 7mm |
| 면광원 밝기 비 | min(1, (D/M/7)²) |
| 별 상 반지름 | r = 0.6 √((138″/D)² + (1.5″)²), 에어리 첫 어둠 138″/D(mm), 시상 1.5″ |
| 기준 최대 배율 | 2D (초과해도 배율은 제한하지 않음, 별 상이 커져 흐려짐) |
| 면광원 적분 등급 문턱 | 5.5 + Δm (+0.5까지 서서히 사라짐) |

망원경 화면 왼쪽 아래에는 구경·초점거리·접안·배율을 한 줄로 표시합니다(`TelescopeHud`). 기본 배치는 GUI 좌표 기준 왼쪽 6px, 바닥에서 6px 위, 글자 크기 0.75배입니다. 핫바와 겹치지 않도록 다음 순서로 배치를 정합니다.

| 단계 | 조건 | 배치 |
| --- | --- | --- |
| 왼쪽 빈 폭 | room = 화면폭/2 − 91 − 29 − 4 − 6 | 91은 핫바 폭 182의 절반, 29는 왼손 칸, 4와 6은 여백 |
| ① | 줄 폭 × 0.75 ≤ room | 0.75배, 바닥에서 6px 위 |
| ② | 줄 폭 × 0.75 > room, 줄 폭 × 0.5 ≤ room | 배율을 room / 줄 폭으로 줄임 (최소 0.5배), 바닥에서 6px 위 |
| ③ | 줄 폭 × 0.5 > room | 0.75배를 유지하고 바닥에서 64px 위 (체력·방어 칸 위)로 올림 |

### 6.5 심원천체 스케치

| 항목 | 사양 |
| --- | --- |
| 판 | `galaxy_atlas.png` 4096×2048 흑백, 천체당 128×128 칸, 사용 462칸 (은하 339, 성운·성단 123). GPU에서는 R8 8MiB |
| 건판 농도 → 밝기 | I = 10^((D − 배경)/γ) − 1, γ = (최대 농도 − 배경)/1.3 |
| 별 제거 | 7px 중앙값 5σ, 잡음 3σ 아래 0, D25 타원 밖 감쇄. 성운 2px·구상성단 3px 흐림 |
| 성운 합성 | DSS2 청색 0.65 + 적색 0.35. 청색과 적색의 모양 상관이 0.5 미만이면 DSS1 적색과 반반 |
| 사진 없는 은하 | 7형태(타원·렌즈형·Sa·Sb·Sc·Sd·불규칙) + 막대: 세르식(n = 2) 팽대부 + 지수 원반 + 2팔 로그 나선 + 옆으로 누운 나선의 암흑대 |
| 구상성단 | King 분포 (조석 반지름 1.1R, 지수 1.3). 핵 반지름 13개는 Harris 목록 (0.035\~0.3R), 나머지 0.12R |
| 행성상성운 | 밝기 지수 1.3 (M27 2.4). M57은 고리 식 (구멍 35%, 고리 0.62R) |
| 표시 문턱 | 적분 등급을 모양 전체에 퍼뜨려 최고 표면 밝기를 구하고, 6.4절·4.12절 문턱과 비교해 그보다 밝은 부분만 회색으로 그림 |
| 구조 문턱 | 팔·막대·먼지띠는 문턱의 2\~8배 이상 밝은 곳에서만. 화면 크기 150→900px에서 구조 문턱 0.3 낮춤 |

### 6.6 오로라

| 항목 | 사양 |
| --- | --- |
| 발생 조건 | 위도 절댓값 60° 초과, 해 고도 −10° 아래, 그날 태양 활동(난수) > 0.4. 67\~75°에서 최대, 달빛에 약해짐 |
| 띠 위치 | 위도 69° − 3° × 세기 |
| 렌더 | 조각 셰이더 부피 레이마칭 (`aurora.vsh`·`aurora.fsh`): 높이 80\~320 km 층, 지구 곡률 포함 |
| 구조 | 커튼 3장 (두께 5 km, 굽이침 ±125 km), 커튼마다 64걸음 + 넓은 빛 장막 12걸음 |
| 색 | 아래 557.7 nm 초록, 위 630 nm 붉은빛 |
| 밝기 | 옆면 상한 정면의 약 4배, 압축 1 − e^(−1.4k) |
| 잡음 | 정수 해시 (그래픽카드와 무관하게 같은 무늬) |

### 6.7 유성

| 유성우 | 극대 해 황경 | ZHR | σ (°) |
| --- | --- | --- | --- |
| 사분의자리 QUA | 283.15° | 110 | 0.6 |
| 거문고자리 LYR | 32.32° | 18 | 1.0 |
| 물병자리 에타 ETA | 45.5° | 50 | 4.0 |
| 페르세우스자리 PER | 140.0° | 100 | 2.5 |
| 오리온자리 ORI | 208.0° | 20 | 4.0 |
| 사자자리 LEO | 235.27° | 15 | 1.5 |
| 쌍둥이자리 GEM | 262.2° | 150 | 1.5 |

활동량은 ZHR × exp(−½(Δλ/σ)²) × sin(복사점 고도) + 산발 유성 10/시간입니다. 게임 밤 길이에 맞춰 시간당 450개를 초당 1개로 환산합니다.

유성의 시간축은 컴퓨터 시계입니다. `System.currentTimeMillis()`를 50으로 나눈 값(초당 20)을 틱 단위로 쓰고, 이를 4틱(0.2초) 칸으로 나눠 칸 번호로 난수를 만듭니다. 각 칸에서 유성 발생 여부, 소속 유성우, 시작 고도(20\~75°)·방위, 지속 시간, 길이, 밝기가 정해지며, 유성 수명이 최대 1초이므로 최근 6칸만 검사합니다. 따라서 하늘 시간의 압축률이나 정지와 무관하게 실제 시간으로 흐르고, 시계가 같은 컴퓨터끼리는 같은 순간에 같은 유성을 봅니다.

---

## 7. 성능

### 7.1 설계상 부하 제어

| 기능 | 방식 |
| --- | --- |
| 망원경 별 | 하늘 칸 41,252개 색인, 시야 근처 칸만 선택, 최대 150,000개 |
| Gaia 칸 캐시 | 배경 스레드 1개, 캐시 상한 300만 별 (메모리상 별 하나 14바이트, 약 42MB). 넘으면 오래 쓰지 않은 칸부터 3/4까지 비움 |
| GPU 메모리 예산 | 그래픽카드 메모리의 1/4, 최소 256MB (`GL_NVX_gpu_memory_info`, `GL_ATI_meminfo`). 조각 : 사진 = 4 : 3. 메모리를 읽지 못하면 512MB + 384MB |
| 큰 텍스처 | 망원경을 손에 들 때 미리 업로드, 2분간 쓰지 않으면 해제 |
| 행성 | 1초 간격 두 시점 계산 + 보간, 7개 1회 28µs |
| 정보 카드 | 150ms 주기 |
| 빛공해 | 289곳을 틱마다 40곳씩, 2초 또는 8블록 이동 시 재계산 |
| 오로라·스케치 | 조각 셰이더 계산, 조건이 없으면 그리지 않음 |
| `/cwhen` | 별도 스레드 1개, 새 요청 시 이전 계산 취소. 1\~150ms (먼 미래 일식은 더 걸림) |
| 별자리 선·이름 | 카메라·하늘이 그대로면 이전 결과 재사용, 시야 근처만 |

### 7.2 측정 결과 (`/cbench`)

`/cbench`는 망원경을 든 밤에 5장면을 장면마다 2초 안정 후 10초 측정하고, 게임 폴더 `cheonsang_bench/날짜_시각/`에 결과와 장면별 스크린샷을 남깁니다.

측정 환경은 Intel Core i7-1255U (12스레드), NVIDIA GeForce RTX 2050 (드라이버 610.91), 시스템 메모리 15.7GB, Java 최대 4096MB, 1920×1080, 렌더 거리 16, 최대 FPS 260, 수직동기화 끔이며, 측정일은 2026-11-08입니다.

| 장면 | 평균 FPS (장노출 / 맨눈 모습) | 느린 1% FPS | 최장 프레임 |
| --- | --- | --- | --- |
| 땅 (기준) | 357.0 / 362.0 | 202.0 / 197.5 | 8.3 / 8.4ms |
| 맨눈 하늘 | 721.1 / 726.3 | 321.2 / 313.6 | 9.9 / 6.6ms |
| 망원경 ×7 은하수 | 534.8 / 246.2 | 244.8 / 128.4 | 8.8 / 15.0ms |
| 망원경 ×75 M31 | 359.0 / 250.4 | 198.4 / 158.6 | 10.7 / 11.1ms |
| 망원경 ×300 목성 | 281.2 / 380.5 | 151.5 / 189.7 | 11.1 / 11.8ms |

자바 메모리 사용량은 733\~992MB였습니다. 이 측정은 망원경 광학 모델, Gaia 별, 심원천체 스케치, 셰이더 오로라가 들어가기 전 빌드에서 이루어졌으며, 현재 구성에서 다시 잰 값은 없습니다. 성능 목표치는 장면별 평균 200 FPS 이상, 느린 1% 120 FPS 이상, 그래픽 메모리 증가 300MB 이하로 정해 두었습니다.

---

## 8. 명령어·키·설정

### 8.1 서버 명령 (`/cele`)

| 명령 | 권한 | 동작 |
| --- | --- | --- |
| `/cele` | 누구나 | 하늘 날짜·시각, 압축률, 월드 기본 위치, 해·달 출몰 |
| `/cele set <년> <월> <일> <시> <분> <위도> <경도>` | OP (게임 마스터) | 하늘 날짜·시각(지방평균시)·월드 기본 위치. 뒤쪽 생략 가능, `~`는 현재 값 유지, 위도 자리에 도시 이름 가능. 연도 −2999\~2999 |
| `/cele set today` | OP | 오늘 날짜로 (시각·위치 유지) |
| `/cele rate <0.01~1000>` | OP | 압축률 (1 = 실제 속도) |
| `/cele reset` | OP | 기본값 (2026-08-11 기준, 0°·0°) |
| `/cele realtime [도시]` · `/cele realtime off` | OP | 실시간 모드 (기본 seoul), 5초(100틱)마다 현실 시각으로 보정. 켜져 있는 동안 set·rate·reset 차단 |

실시간 도시는 longyearbyen, reykjavik, london, newyork, seoul, cairo, hongkong, singapore, lima, sydney, buenosaires, ushuaia, mcmurdo의 13곳입니다.

### 8.2 클라이언트 명령

| 명령 | 동작 |
| --- | --- |
| `/cplace` · `/cplace <위도> <경도>` · `/cplace <도시>` · `/cplace reset` | 개인 관측 위치 보기·설정·해제 (자기 하늘만) |
| `/cfind <이름> [true\|false]` | 천체 찾기 (고도·방위, 60초 방향 화살표). `true`: 시점 이동 + 망원경 추적 고정 |
| `/cwhen` | 오늘 밤 볼거리 |
| `/cwhen <천체>` | 가장 좋은 밤 (범위: 고정 천체 1년, 행성·소행성·위성 3년, 달 60일, 달 지명 120일) |
| `/cwhen solar total\|annular\|partial` | 다음 일식 (60년 안) |
| `/cwhen lunar total\|partial\|penumbral` | 다음 월식 |
| `/cwhen meteor [QUA\|LYR\|ETA\|PER\|ORI\|LEO\|GEM]` | 다음 유성우 극대 |
| `/cwhen full` · `new` · `conj` · `next` | 다음 보름·삭·행성 근접·다음 현상 목록 |
| `/clog` · `/clog list` | 관측 수첩 열기, 도감 요약 |
| `/cbench` · `/cbench stop` | 성능 측정 |
| `/cvis` · `/cvis <효과> on\|off` | 가시성 효과 상태·켜고 끄기 (저장 안 함). 효과: twilight, moon, airglow, extinction, refraction, colorloss, light, dark, zodiacal, twinkle, aurora |

`/cwhen` 천체 판정 기준(해 고도·천체 고도)은 심원천체 −18°·25°, 맨눈 별 −12°·15°, 어두운 별 −15°·20°, 행성 −6°·8°(수성·금성 −3°·5°)입니다. 밤마다 20분 간격으로 점수를 내고 5분 간격으로 다듬습니다.

### 8.3 키와 망원경 조작

| 동작 | 기본 키 |
| --- | --- |
| 망원경 켜기·끄기 | 아이템 우클릭 |
| 접안렌즈 변경 | 마우스 휠 |
| 구경 변경 | 왼쪽 Shift + 휠 |
| 표시 순환 (전부 → 선만 → 이름만 → 끄기) | N |
| 천체 선택 | 좌클릭 |
| 추적 고정 | 왼쪽 Alt + 좌클릭 |
| 각거리 | Ctrl + 좌클릭 |
| 선택 해제 | Shift + 좌클릭 |
| 천상 설정 화면 · 관측 수첩 | 지정 없음 (조작 설정 "기타"에서 지정) |

망원경 아이템 `cheonsang:telescope`는 최대 1개씩 쌓이며 크리에이티브 "도구 및 유틸리티" 탭의 망원경(spyglass) 뒤에 놓입니다. 조합법은 위 가운데 유리판, 가운데 망원경(spyglass), 아래 줄 철 주괴 3개입니다.

### 8.4 설정 화면 항목

별자리 선·이름·천체 표시·조준선, 별자리 체계(서양 88 / 천상열차분야지도), 적도 격자(천구 적도·황도·백도·교점), 지평 격자, 경계선, 별자리 그림, 정보 카드(켜기·위치·투명도 25\~100%·표시 줄), 글씨 크기(×0.75\~×1.5), 화면 낮밤 = 천상 해, 관측 위치 입력, 팩 상태 항목이 있습니다.

---

## 9. 멀티플레이 동기화와 악용 방지

### 9.1 동기화

| 대상 | 방식 |
| --- | --- |
| 하늘 시계·위치 | 서버가 차원별로 저장하고 접속 다음 틱, 차원 이동 시, 값 변경 시 `sky4`를 보냄 |
| 개인 위치 | 클라이언트에만 저장. 월드 기본 위치(위도·경도)가 바뀌면 서버의 `#gen` 번호가 1 증가하고, `placegen`을 받은 클라이언트는 번호가 다르면 `/cplace`를 해제 (접속하지 않았던 플레이어도 다음 접속 때). 날짜·시각·압축률만 바뀌면 유지 |
| 관측 도감 | 클라이언트가 가시성을 판정해 `log_add` 송신, 서버가 그 순간의 하늘 율리우스일을 붙여 저장. 목록 재전송은 1초(20틱)에 한 번 모아서 |
| 발전과제 | 도감에서 판정, `adv:<id>/<조건>` 키로 서버가 조건 부여 |
| 망원경 자세 | 상태가 바뀔 때만 `pose_set` 송신, 서버가 다른 플레이어에게 `pose` 중계. 새 접속자에게 현재 확대 중인 목록 전송, 퇴장 시 해제. 저장 안 함 |
| 유성 | 패킷 없음. 각 클라이언트가 컴퓨터 시계(`System.currentTimeMillis`)의 0.2초 칸 번호로 같은 난수를 만들어, 시계가 맞는 클라이언트끼리 같은 유성 |
| 서버에 천상이 없을 때 | 클라이언트가 기본값(2026-08-11, 0°·0°, 압축률 1) 또는 설정 화면 값으로 단독 계산 |

### 9.2 클라이언트 → 서버 패킷 제한

플레이어별 토큰 통(token bucket) 방식입니다. 한꺼번에 burst개까지 허용하고 이후 초당 perSec개씩 다시 채우며, 넘는 패킷은 버립니다. 퇴장 시 통을 지웁니다.

| 패킷 | burst | 초당 | 비고 |
| --- | --- | --- | --- |
| `log_add` (도감·발전과제) | 300 | 20 | 접속 직후 몰아서 보내는 양보다 넉넉한 값 |
| `pose_set` (망원경 자세) | 6 | 2 | 끄기(on = false)는 제한 없이 받음 |

### 9.3 입력 검증

| 항목 | 규칙 |
| --- | --- |
| 도감 키 | `^[A-Za-z]+:[^\t\n\|]{1,80}$` 또는 `sun`·`moon` |
| 이름 | 탭·줄바꿈·`\|` 치환, 80자로 자름 |
| 배율 | 1\~1000으로 제한 |
| 항목 수 | 한 사람 최대 15,000, 이미 있는 키는 무시 (최초 발견만 기록) |
| 발전과제 ID·조건 | `^[a-z0-9_]{1,32}$` · `^[A-Za-z0-9_]{1,32}$`, 서버에 존재하는 `cheonsang:` 발전과제만 |
| 문자열 길이 | 패킷 코덱에서 key·name 96바이트, 목록 문자열 256바이트·최대 20,000줄 |
| 서버 설정 | `rate` 0.01\~1000, `lat` −90\~90, `lon` −180\~180으로 제한 |

---

## 10. 장애 격리

클라이언트 기능은 하늘, 망원경, 찾기, 키, 오늘 밤, 수첩, 도감 단위로 나뉘어 `Safety.run`으로 감싸져 있습니다. 한 기능에서 예외가 나면 그 기능만 이번 접속 동안 끄고 기본 하늘이나 바닐라 동작으로 되돌리며, 전체 스택은 `latest.log`에 한 번 기록하고 화면에 기능 이름과 함께 오류를 표시합니다. `VirtualMachineError`는 다시 던집니다. 서버에 다시 접속하면 모든 기능이 다시 켜집니다. 셰이더를 쓸 수 없는 환경에서는 은하 스케치가 단순 빛무리로 대체됩니다.

---

## 11. 알려진 제한

| 분야 | 내용 |
| --- | --- |
| 호환성 | Iris(셰이더 모드)와 함께 쓸 수 없음 (`breaks` 선언) |
| 위치 계산 | 별에 장동·광행차·고유운동 미적용 (Stellarium 대비 약 1′). 달 물리 칭동·장동 제외 (0.04° 안팎) |
| 시간 범위 | 행성은 1800\~2050년 최대 12.6″, 기원전 1000년\~서기 3000년 최대 약 2′. 토성 위성·세레스·베스타는 1900\~2050년 자료 기준, 소행성은 범위 밖 미표시. ΔT는 1900년 이전과 2150년 이후 장기 포물선 |
| 목성 위성·대적점 | 목성 위성 저정밀식 (약 0.5 목성 반지름). 대적점 경도는 직선 근사라 2026년에서 멀어질수록 어긋남 |
| 하늘 밝기 | 천정 한 점 값을 전체에 적용. 날씨·구름·대기 투명도 변화 없음. 시상 1.5″·소광 계수 고정 |
| 광학 | 수차·중앙 차폐·투과율 없음. 암순응 회복 30초는 게임용으로 줄인 값 |
| 표시 | 15.2등보다 어두운 별은 은하수 영상으로만 표현 |
| 유성 | 컴퓨터 시계 기준이라 시계가 어긋난 클라이언트끼리는 다른 유성을 봄 |
| 스케치 | 판에 밉맵이 없어 아주 작게 보일 때 반짝일 수 있음. DSS 핵 포화로 밝은 은하 핵이 납작. 칸 128px이라 큰 천체 고배율에서 흐림. DSS 판은 비상업 조건 (CC BY-NC-SA 4.0) |
| `/cwhen` | 고도·어둠·달빛만 판정 (빛공해·날씨 제외). 위성은 모행성 위치로 계산. 달 지명은 칭동 무시. 압축률이 1이 아닌 월드에서 표시 시각이 어긋날 수 있음 |
| 위치 | OP가 `/cele set`으로 위도·경도를 바꾸면 모든 플레이어의 `/cplace`가 해제됨. 실시간 모드에서 `/cplace`로 경도를 바꾸면 하늘의 세계시가 현실과 경도 차만큼 어긋남 |
| 천상열차분야지도 | 현대 별과 맞출 수 없는 5개(천직·천묘·동구·군문·기부) 미포함. 경계·그림·별 부호는 서양 체계만 |
| 게임 규칙 | 몹 생성·잠자기는 바닐라 10분 하루 (`server.json`의 `vanillaNight`로 해 고도 −6° 기준 전환 가능) |
| 성능 측정 | 현재 구성에서 `/cbench` 재측정 값 없음. 저사양(내장 그래픽) 최소 사양, 전용 서버 다인원 부하 미측정 |
