<p align="center"><img src="icon_512.png" width="128" alt="Cheonsang icon"></p>

# Cheonsang (천상)

A Fabric mod for Minecraft 26.3 that replaces the overworld sky with the real celestial sphere for any date and any place on Earth.

[한국어](#천상-한국어)

## Features
- Positions of stars, Sun, Moon, planets and moons computed for the observer's date and location. Planets use a truncated VSOP87 theory (1,259 terms); the maximum difference from PyEphem for 1800–2050 is 2.4″ (Neptune) to 12.6″ (Mars).
- Stars: HYG to magnitude 9, Tycho-2 to 13, and with the optional extra pack 32,061,510 Gaia DR3 stars to magnitude 15.2
- Telescope with real optics: aperture 50–305 mm, eyepieces 40–3 mm, limiting magnitude, diffraction, exit pupil
- Galaxies, nebulae and globular clusters drawn as seen in an eyepiece, based on Digitized Sky Survey plates
- Sky brightness model: twilight, moonlight, airglow, light pollution, dark adaptation, extinction and refraction
- Solar and lunar eclipses for the observer's place, meteor showers, aurora, zodiacal light
- Western 88 constellations or the Korean Cheonsang Yeolcha Bunyajido (289 asterisms)
- `/cfind` to find any object, `/cwhen` for when and where objects and events are visible, `/cplace` for a personal observing location
- Screen day and night follow the real Sun altitude; mob spawning and sleeping stay vanilla
- Korean and English

## Documentation
Korean documentation is in [docs/](docs/README.md): user manual, overview, technical specification and implementation analysis. Change history: [CHANGELOG.md](CHANGELOG.md).

## Download
See [Releases](../../releases).
- `cheonsang-2.0.0.jar`: the mod (required on client and server)
- `cheonsang-extra-1.0.1.jar`: optional pack with Gaia stars to magnitude 15.2, Milky Way 16K/32K and an 8K Moon map (about 434 MB)

## Requirements
Minecraft 26.3, Fabric Loader 0.20.1 or later, Fabric API, Fabric Language Kotlin, Java 25.
On servers, install the mod on both the server and the clients.

## Building
```
./gradlew build
```
The jar is written to `build/libs/`. The repository contains the mod source and bundled data; the optional extra pack is distributed only as a release file.

## Known limitations
- Not compatible with Iris shaders
- `/cwhen` visibility uses altitude, darkness and moonlight only (no light pollution or weather)
- Stars use J2000 positions with precession only (no nutation, aberration or proper motion); the difference from Stellarium is about 1′
- Meteors follow the computer clock, so multiplayer players see the same meteors only as closely as their clocks agree

## Development
Planning, data selection, design decisions and in-game verification by itsnotfineart. The source code was written in collaboration with a generative AI (Anthropic Claude).

## License
Source code: MIT (see `LICENSE`). Original textures and icons by itsnotfineart: CC BY-SA 4.0.
Data files keep the licenses of their sources (see `CREDITS.txt`). The deep-sky sketch atlas is derived from the Digitized Sky Surveys and is CC BY-NC-SA 4.0 (non-commercial).

---

# 천상 (한국어)

마인크래프트 26.3 Fabric 모드. 오버월드 하늘을 날짜와 지구상 위치에 맞는 실제 천구로 바꾼다.

## 기능
- 관측 날짜·위치 기준 별·해·달·행성·위성 위치. 행성은 VSOP87 축약 이론(1,259항)으로 계산하며, 1800~2050년 PyEphem 대비 최대 차이는 2.4″(해왕성)~12.6″(화성)
- 별: HYG 9등, Tycho-2 13등, 선택 extra 팩 사용 시 Gaia DR3 32,061,510개 15.2등
- 실제 광학 계산 천체망원경: 구경 50~305mm, 접안 40~3mm, 한계등급, 회절, 사출동공
- 은하·성운·구상성단의 접안렌즈 관측 모습 (Digitized Sky Survey 사진 기반)
- 하늘 밝기 모델: 박명, 달빛, 대기광, 빛공해, 암적응, 소광, 굴절
- 관측지 기준 일식·월식, 유성우, 오로라, 황도광
- 서양 88 별자리 또는 천상열차분야지도 289 별자리
- `/cfind` 천체 찾기, `/cwhen` 관측 시각·장소 예보, `/cplace` 개인 관측 위치
- 화면 낮밤은 실제 해 고도를 따름. 몹 생성·잠자기는 바닐라 규칙
- 한국어, 영어

## 문서
[docs/](docs/README.md): 사용자 매뉴얼, 소개서, 기술 사양서, 과학적 구현 분석서. 변경 이력은 [CHANGELOG.md](CHANGELOG.md).

## 내려받기
[Releases](../../releases)
- `cheonsang-2.0.0.jar`: 본체 (클라이언트·서버 필수)
- `cheonsang-extra-1.0.1.jar`: 선택 팩. Gaia 별 15.2등, 은하수 16K·32K, 달 8K (약 434MB)

## 필요 환경
Minecraft 26.3, Fabric Loader 0.20.1 이상, Fabric API, Fabric Language Kotlin, Java 25.
서버에서는 서버와 클라이언트 모두 설치.

## 알려진 제한
- Iris 셰이더와 함께 쓸 수 없음
- `/cwhen`의 가시 판정은 고도·어둠·달빛만 반영 (빛공해·날씨 제외)
- 별은 J2000 좌표에 세차만 적용 (장동·광행차·고유운동 제외), Stellarium과 약 1′ 차이
- 유성은 컴퓨터 시계 기준. 멀티플레이에서 같은 유성이 보이는 정도는 각 컴퓨터 시계가 맞는 정도에 따름

## 개발
기획, 자료 선정, 설계 판단, 게임 내 검증은 itsnotfineart가 맡았고, 소스 코드는 생성형 AI(Anthropic Claude)와 협업해 작성했다.

## 라이선스
소스 코드 MIT (`LICENSE`). itsnotfineart 자체 그림·아이콘 CC BY-SA 4.0.
자료 파일은 원 자료의 라이선스를 따름 (`CREDITS.txt`). 심원천체 스케치 판(DSS 기반)은 CC BY-NC-SA 4.0 (비상업).
