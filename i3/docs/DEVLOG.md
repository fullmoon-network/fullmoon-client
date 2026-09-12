# i3 개발 로그

각 항목은 코드가 아니라 **증거**를 남긴다. 무엇을 만들었는지가 아니라 무엇을 실행해서 무엇을
봤는지를 쓴다. 캡처 절차는 [evidence/README.md](evidence/README.md), 페이즈 범위는
[PLAN.md](PLAN.md).

## 2026-08-29 · P1-A 레이아웃 산술, 상태 모델, 포커스 링

게임 클래스를 한 줄도 참조하지 않는 다섯 파일로 시작했다. 위젯을 그리기 전에 위젯이 서 있을
좌표계와 "지금 어떤 상태인가"를 먼저 확정해야 하고, 그 둘은 마인크래프트 없이 JUnit으로 전수
검증이 가능한 유일한 부분이다.

- `layout/Box` — 이 클라이언트에서 공간을 나누는 유일한 산술. 모든 메서드가 새 박스를 반환하고,
  뒤집힌 좌표쌍은 음수가 아니라 0으로 붕괴한다. `col`/`row`의 나눗셈 나머지는 앞쪽 열에 1px씩
  분배한다 — 마지막 열에 몰아주면 오른쪽 끝이 삐뚤어진다.
- `layout/Stack` — 밴드 커서. **의도적으로 클램프하지 않는다.** 넘치는 밴드를 영역 안으로
  접어 넣으면 화면이 조용히 틀어지고, 넘침은 `overflows()`로 잡아서 다시 배치해야 할 사건이다.
- `ui/State` — 8상태(rest/hover/active/focus/focus-visible/disabled/loading/error)와 `Signals`
  → `State` 우선순위 하나. 우선순위를 위젯마다 두면 같은 화면의 두 위젯이 자기 상태를 다르게
  판정한다. `FOCUS`와 `FOCUS_VISIBLE`을 분리한 이유는 도착 경로가 다르기 때문이다: 클릭은 링
  없이 포커스를 남기고, Tab은 링을 켜야 한다.
- `ui/Chrome` — 상태 → (fill, ink, line) 토큰 매핑. 목소리는 `quiet`와 `loud` **둘뿐이고**,
  loud가 두 개 있는 화면은 자기가 무엇을 위한 화면인지 결정하지 못한 화면이다.
- `ui/Focus` — 순회 링. 위치와 가시성이 별개 상태다. `advance(step)`은 링을 켜고 양방향으로
  래핑하며 포커스를 받지 않는 대상을 건너뛴다. `point(target)`은 링을 켜지 않는다(클릭 경로).
  포커스를 쥔 컨트롤이 도중에 disabled가 되면 `held()`는 null을 반환하지만 **위치는 남긴다** —
  다시 활성화되면 그 자리로 돌아온다.

### 증거

`gradlew -p i3/mod build test --console=plain` → `BUILD SUCCESSFUL`. 테스트 47개, 실패 0:

| 클래스 | tests | failures |
| --- | --- | --- |
| `layout.BoxTest` | 10 | 0 |
| `layout.StackTest` | 7 | 0 |
| `ui.StateTest` | 5 | 0 |
| `ui.FocusTest` | 17 | 0 |
| `ui.ChromeTest` | 8 | 0 |

초록불 자체는 증거가 약하므로 **뮤테이션 한 번**을 돌렸다. `Box.col`에서 나머지 분배
(`Math.min(index, spare)`)를 빼고 `--tests BoxTest`를 실행:

```
> Task :test FAILED

BoxTest > columnsTileTheBoxExactly() FAILED
    org.opentest4j.AssertionFailedError at BoxTest.java:76

10 tests completed, 1 failed
```

즉 타일링 테스트는 실제로 물고 있다. 원본 복원 후 전체 재실행도 `BUILD SUCCESSFUL`.

`ChromeTest.aPressDarkensRatherThanLifts`는 hex를 눈으로 비교하지 않고 상대 휘도를 계산한다 —
`accent.pressed`가 `accent`보다 어둡다는 건 측정값이어야 하고, quiet은 어둡게 할 fill이 없어서
`accent.wash`로 올라간다는 것도 같은 함수로 확인한다.

### 아직 아닌 것

- 캡처 없음. P1-A는 픽셀을 하나도 그리지 않는다 — 위젯이 없으므로 상태 매트릭스 캡처는 P1-B
  이후다.
- `Focus`는 아직 어떤 서피스에도 연결되지 않았다. 키보드 순회 캡처는 P1-D 몫이다.

## 2026-08-29 · P1-B 서피스 이벤트 라우팅, 버튼과 스위치

포인터와 키보드를 받는 물건이 처음 생겼다. 규칙은 컨트롤이 아니라 `ui/Surface` 한 곳에 있고,
`Surface`는 **그리지 않는다** — 그리는 순서는 레이아웃의 몫이다(팝오버는 자기를 띄운 행 위에
칠해야 하고, 둘을 동시에 아는 건 레이아웃뿐이다). 그래서 게임 클래스가 이 경로에 하나도 없고,
`InputConstants`의 키 번호는 javac가 바이트코드에 접어 넣는 상수라 헤드리스 JUnit에서 클래스
로딩 없이 규칙을 전수 검증할 수 있다.

- `ui/Surface` — 등록 순서가 Tab 순서, 나중에 등록한 위젯이 포인터를 먼저 먹는다. 캡처는
  누른 위젯이 쥐고, 눌린 상태는 포인터를 따라 경계를 드나든다. 죽은 컨트롤은 클릭을
  **삼킨다** — 뒤로 흘려보내면 컨트롤이 꺼져 있다는 이유로 플레이어의 조준을 벌하는 셈이다.
- `ui/Button`, `ui/Toggle` — `draw(Painter, State)` 하나로 여덟 상태를 전부 그린다.
- 링이 상태에서 빠져나왔다. `State`는 한 값이라 "지금 참인 것 중 가장 큰 것"만 말할 수 있는데,
  포커스는 포인터가 도착하거나 요청이 나가는 순간 그 경쟁에서 진다. 링은 서피스가 소유하는
  별개의 비트다.

### 증거

`gradlew -p i3/mod clean build test --console=plain` → `BUILD SUCCESSFUL in 3s`. 테스트 72개,
실패 0:

| 클래스 | tests | failures |
| --- | --- | --- |
| `layout.BoxTest` | 10 | 0 |
| `layout.StackTest` | 7 | 0 |
| `text.TypesetTest` | 6 | 0 |
| `ui.StateTest` | 5 | 0 |
| `ui.FocusTest` | 17 | 0 |
| `ui.SurfaceTest` | 18 | 0 |
| `ui.VoiceTest` | 9 | 0 |

뮤테이션 한 번. `Surface.key`의 `live()` 게이트를 빼서 요청이 떠 있는 컨트롤도 Enter에 반응하게
되돌리고 `--tests '*SurfaceTest'`:

```
> Task :test FAILED

SurfaceTest > aControlInFlightKeepsTheRingAndAnswersNothing() FAILED
    org.opentest4j.AssertionFailedError at SurfaceTest.java:280

18 tests completed, 1 failed
```

원본 복원 후 재실행 `BUILD SUCCESSFUL`. 캡처는 [evidence/README.md](evidence/README.md)의 P1-B
표 세 개 — 매트릭스 32칸, 마우스 없는 Tab 4정거장, `loading` 대 `disabled` 2배 크롭.

캡처 절차 자체를 `tools/capture.py`로 굳혔다. Xvfb, Gradle 런, 아틀라스 대기, XTEST 탭, 루트
윈도우 `import`이 손으로는 여섯 번 틀릴 수 있는 일이었고, 순회 캡처는 정거장마다 **같은
서피스**여야 의미가 있어서 샷 사이에 화면을 다시 열지 않는 규칙이 도구에 들어가 있다.

### 캡처가 잡은 것

- 마스트헤드의 액센트 바가 워드마크 **옆**이 아니라 **아래**에 있었다. 베이스라인은 어떤
  프로바이더든 드로 원점 +7px인데 바를 역할의 공칭 박스로 재고 있었으니, 22px 페이스는 몸통
  대부분을 원점 위에 그리고 바는 베이스라인에 걸린다. `Typeset.capTop`/`capHeight`가 대문자
  밴드를 이름 붙이고 바는 거기서 잰다. 픽셀로 확인: 틱 28..61행, 워드마크 잉크 26..61행 —
  아래가 정확히 맞고 위는 1 gui px 안쪽(세리프 오버슛)이다.
- 스위치의 `loading`이 `disabled`와 사실상 같은 그림이었다. 버튼은 라벨을 점 셋으로
  바꾸는데 스위치는 노브 밝기만 달랐다. 요청이 떠 있는 스위치는 **위치 사이**에 있는 것이므로
  노브를 트랙 중앙에 세운다.

### 아직 아닌 것

- `SpecimenScreen`은 아직 자기 마스트헤드 사본을 들고 있어서 커밋된 P0 캡처에 위 버그가 남아
  있다. `DevChrome`으로 옮기는 건 두 화면이 탭 레일을 공유하는 P1-D에서 같이 한다.
- 슬라이더·셀렉트·텍스트필드가 없으니 드래그와 캐럿 경로는 `Surface`에서 아직 죽은 코드에
  가깝다. `scroll`도 받는 위젯이 없다.

## 2026-08-29 · P1-C 슬라이더, 셀렉트, 텍스트 필드

포인터를 **붙잡는** 컨트롤 셋이 한꺼번에 들어왔다. 드래그, 캐럿, 그리고 자기 아래 두 줄을 덮는
팝오버 — P1-B까지의 서피스는 클릭 한 번으로 끝나는 컨트롤만 알고 있었다. 매트릭스가 4행에서
7행(56칸)으로, 라이브 밴드가 한 줄에서 세 줄로 늘었고, 그래서 키트가 640×360 gui px에 더는
들어가지 않는다.

- `ui/Slider` — 값은 float가 아니라 **step 격자 위의 int**다. float는 보여주기 전에 포맷을
  거쳐야 하고 그때마다 다르게 반올림돼서 볼륨이 0.7300000001로 읽히는 사고가 난다. 격자를 아는
  컨트롤은 화살표로 한 칸 밀 수 있고 소수점 결정 없이 인쇄된다. `loading`은 노브를 그 자리에
  두고 **숫자만** 뺀다 — 위치는 로컬이고 여전히 참인데 숫자는 아무도 확인해주지 않은 부분이다.
  `Toggle`은 같은 이유로 정반대를 한다(거기서는 위치가 바로 의심스러운 쪽이다).
- `Slider.valueAt`/`knobAt`은 트래블을 바운즈에서 읽지 않고 `left..right` 두 정수로 받는다.
  트래블의 양 끝은 **잰 텍스트**지만 위치→step 매핑은 아니고, 못 박아둘 값어치가 있는 건 그
  매핑이며, 그 테스트는 폰트를 얻으려고 게임을 띄울 수 없다.
- `ui/Select` — `open`은 상태가 아니다. 여덟 상태는 컨트롤이 **어떻게 다뤄지는 중인지**를
  말하고, 열린 셀렉트는 hover든 focus든 in-flight든 될 수 있다. open이 바꾸는 건 클릭이 닿는
  범위(`reach`)와 무엇 위에 그리는지(두 번째 패스)뿐이다. 화살표는 손으로 놓은 픽셀 3행이다 —
  셰이프 파이프라인에 삼각형이 없고, 폰트에서 빌려온 글리프는 이 클라이언트가 안 닮으려고
  존재하는 바로 그 바닐라 크롬이다.
- `ui/TextField` — 캐럿 모델은 **아무것도 재지 않는다**. 인덱스는 전부 문자열 오프셋이라 폰트
  없이, 따라서 게임 없이 돌아간다. 진짜 재야 하는 둘(클릭이 떨어지는 인덱스, 뷰가 밀린 거리)만
  그리는 도중에 정해진다. 인덱스는 char가 아니라 **code point**로 움직인다 — 이름 칸은
  플레이어가 기본 다국어 평면 밖 문자를 넣는 바로 그 자리고, 서로게이트 쌍 사이에 선 캐럿은
  다음 백스페이스에 반 글자를 데려간다.
- `ui/Surface.at` — 히트 테스트가 두 패스가 됐다. 등록 순서 = Tab 순서는 그대로 두고, 서피스
  위에 뜬 위젯을 먼저 훑는다. 열려서 위에 그린다는 사실과 먼저 클릭된다는 사실이 같은 사실이어야
  하는데 한 패스로는 "나중에 등록된 것이 이긴다" 하나뿐이었다.
- `ui/KitScreen` — 7행 × 8상태와 밴드 세 줄이 같은 `draw(Painter, State)`를 지난다. 밴드가
  라우팅을 보여주는 자리다: 적용이 밴드 전체를 띄우고 취소가 되돌리고 스위치가 적용을 켜고
  끈다 — `loading`과 `disabled`가 표로만이 아니라 마우스로도 도달 가능해진다.
- `tools/capture.py` + `build.gradle.kts` — 지오메트리와 gui scale이 인자가 됐고, 한 숫자가
  Xvfb와 **클라이언트 창**을 같이 잡는다. `guiScale`은 매 런 앞에서 `options.txt`에 핀으로
  박는다(게임이 나갈 때 이 파일을 다시 쓰고, `run/`은 레포에 없다).

### 증거

`gradlew -p i3/mod clean build test --console=plain` → `BUILD SUCCESSFUL in 2s`. 테스트 108개,
실패 0:

| 클래스 | tests | failures |
| --- | --- | --- |
| `layout.BoxTest` | 10 | 0 |
| `layout.StackTest` | 7 | 0 |
| `text.TypesetTest` | 6 | 0 |
| `ui.StateTest` | 5 | 0 |
| `ui.VoiceTest` | 9 | 0 |
| `ui.FocusTest` | 17 | 0 |
| `ui.SurfaceTest` | 18 | 0 |
| `ui.SliderTest` | 8 | 0 |
| `ui.SelectTest` | 11 | 0 |
| `ui.TextFieldTest` | 17 | 0 |

뮤테이션 한 번. `Select.overlaying()`을 `return false`로 되돌리고 `--tests '*SelectTest'`:

```
> Task :test FAILED

SelectTest > anOpenListIsHitBeforeWhateverItCovers() FAILED
    org.opentest4j.AssertionFailedError at SelectTest.java:148

11 tests completed, 1 failed
```

원본 복원 후 재실행 `BUILD SUCCESSFUL in 1s`.

레이아웃 산술은 캡처에서 직접 쟀다. `content.x()` 열(device x=442)을 훑으면 규칙선이 gui
y=122(캡션 밴드 아래)와 124+32k에 앉아 있다 — 156, 188, 220, 252, 284, 316, 348. 7행이 32px
피치로 348에서 끝나고, liveTop 364, 밴드 세 줄 383·415·447, 마지막 컨트롤 밑변 471,
`footerY(540)` = 505. 열린 리스트는 409..465를 차지하니 슬라이더 줄(415)과 버튼 줄(447)
**둘 다** 밑에 깔린다 — `p1c-select-open-960x540.png`이 그 장면이다.

캡처 여섯 장은 [evidence/README.md](evidence/README.md)의 P1-C 표.

### 캡처가 잡은 것

- 텍스트 필드의 `loading` 칸이 `달빛`이 아니라 `빛 •••`이었다. `view()`는 캐럿을 화면 안에
  잡아두고, `loading`은 점 셋 자리를 만들려고 영역을 좁히고, 포커스 없는 필드도 캐럿 인덱스는
  글 끝에 남아 있다 — 그래서 좁아진 순간 `달`이 왼쪽으로 밀려 나갔다. 아무도 타이핑하지 않는
  필드가 보여줘야 하는 건 글의 **머리**다: 스크롤이 `typing ? view(area) : 0`이 됐다. 같은 캡처
  다섯 장을 수정 전/후로 두 번 찍어 픽셀로 비교하면 다른 픽셀이 **177개**, 전부 x 1284..1301,
  y 654..669 — 그 한 칸이다. 나머지가 완전히 일치한다는 건 리그가 픽셀 단위로 재현된다는
  뜻이기도 하다.
- 첫 1920×1080 캡처가 레터박스로 나왔다. 검은 매트 위에 1280×720 클라이언트, 여전히 640×360
  gui px, 푸터 글자가 마지막 매트릭스 행 위에 겹쳐 있었다. `--geometry`는 Xvfb만 잡고 창
  크기는 `build.gradle.kts`에 박힌 `programArgs("--width", "1280", ...)`에서 오고 있었다.
  측정값: `root 1920x1080; lit box x 320..1599 (1280) y 180..899 (720)`. 이제 크기는
  `gradle.properties`의 `client_width`/`client_height`고 리그가 둘 다 넘긴다.

### 아직 아닌 것

- `scrolled`는 폰트가 필요해서 JUnit이 못 만진다(`Typeset.width` → `Minecraft.getInstance()`).
  위 수정의 증거는 테스트가 아니라 캡처 두 장의 픽셀 차이다. 캐럿 모델 자체는 폰트를 안 쓰므로
  `TextFieldTest` 17개가 전수로 덮는다.
- `Surface.scroll`을 받는 위젯이 아직 없다. 슬라이더에 휠을 붙이는 건 스크롤되는 패널 안에서
  값이 튀는 사고와 붙어 있어서, 패널이 생기는 P1-D의 `ListRow`와 같이 결정한다.
- `SpecimenScreen`은 여전히 자기 마스트헤드 사본을 들고 있어서 커밋된 P0 캡처에 P1-B가 고친
  캡 밴드 버그가 남아 있다. `DevChrome`으로 옮기는 건 두 화면이 탭 레일을 공유하는 P1-D에서.

## 2026-08-29 · P1-D 목록, 탭 레일, 툴팁, 개발 서피스 크롬

P1-C까지의 컨트롤은 전부 자기 하나로 끝났다. 이번엔 **다른 컨트롤로 만들어진 컨트롤**(행이 든
우물), 자기 값이 아닌 것에 표시를 다는 컨트롤(탭 레일), 그리고 서피스 위에서 **아무도 도달할 수
없는 것**(툴팁)이 들어왔다. 셋 다 여덟 상태 모델을 그대로 쓰지만, 셋 다 그 모델이 답하지 못하는
걸 하나씩 들고 온다 — 선택, 예행 표시, 힌트.

크롬도 페이지에서 나왔다. `SpecimenScreen`이 들고 있던 마스트헤드 사본이 사라지고 세 페이지가
`DevScreen` 하나를 상속한다. P1-C 마지막 줄에 적어둔 빚이다. [PLAN.md](PLAN.md)의 P1 위젯 여덟
개 — 버튼, 토글, 슬라이더, 셀렉트, 텍스트 필드, 행, 탭 레일, 툴팁 — 가 이걸로 다 찼다.

- `ui/ListRow` — **선택은 상태가 아니다.** 여덟 상태는 컨트롤이 지금 어떻게 다뤄지는 중인지를
  말하는데 고른 행은 그 여덟 개보다 오래 산다. 마우스가 떠난 행도, 리스트가 꺼진 행도 여전히
  고른 행이다. 그래서 선택은 아홉 번째 상태가 아니라 여덟 개 **위에** 얹힌 틱이고, 링이 아니다 —
  링은 컨트롤 바운즈 **밖에** 그리는 것이고 행의 밖은 다음 행이며, 스크롤되는 뷰포트가 세 변을
  잘라 간다. 쉬는 행의 바닥은 `Voice`가 주는 쉬는 색이 아니라 **우물이 비쳐 보이는 것**이다.
  보이스의 쉬는 바닥을 마흔 줄 쌓으면 버튼 마흔 개로 읽히고, 토큰 쪽에서도 같은 말을 한다:
  `surface.raised`의 용도가 "hovered row, selected list item ground"다.
- `ui/ListPanel` — 리스트 전체가 **키보드 정류장 하나**다. 모드 마흔 개가 Tab 뒤에 있으면 그
  아래 버튼까지 서른아홉 번이다. 휠과 화살표는 같은 곳에 다른 문으로 들어온다 — 휠은 뷰를
  옮기고 표시를 그대로 두고, 화살표는 표시를 옮기고 뷰를 끌고 온다. 그래서 휠로 굴려 놓고 Down을
  누른 사람이 보던 행을 잃지 않는다. `thumbH`/`thumbY`/`firstAt`은 행 높이가 고정이라 전부 정수
  산술이고 전부 static이다: 폰트도 창도 게임도 없이 못 박힌다. 죽은 행은 표시를 **받고** 고르기만
  거절한다(표시가 죽은 행을 뛰어넘으면 리스트에 그 행이 없는 것처럼 읽힌다). 리스트가 잡는
  포인터는 썸 하나뿐이고, 행에서 시작한 드래그는 아무것도 아니다.
- `ui/TabRail` — 화살표는 **예행이고 커밋이 아니다.** 네 번째 탭까지 걸어가는 동안 페이지 세
  개를 로드하는 게 표시가 존재하는 이유고, 여기서 탭 하나는 화면 전체다. `blurred()`는 표시를
  지금 떠 있는 페이지로 되돌린다 — 레일을 떠나는 건 레일에서 고르는 게 아니다. `pick`은 이름
  너비를 표로 받아서 히트 테스트가 폰트 없이 검증된다.
- `ui/Tooltip` — `Widget`이 아니고 여덟 상태도 없다. 아무것도 툴팁을 hover할 수 없고 키보드를
  올릴 수도 없다 — 서피스에서 플레이어가 도달할 수 없는 유일한 것이고, 그래서 **무엇도 툴팁에만
  적혀 있어서는 안 된다**. 한 줄인 것도 같은 이유다: 두 줄이 필요한 힌트는 문서고 서피스 본문에
  있어야 한다. 뜨는 자리는 창이 아니라 **서피스가 넘겨준 영역**이고, 갭은 컨트롤과 영역을 닫는
  위아래 두 규칙선에만 붙는다. 좌우는 페이지 전체가 이미 정렬한 변이라 갭을 넣으면 정렬이 깨진다.
- `ui/Widget.hint` + `ui/Surface.tipped` — 힌트는 기본이 빈 문자열이고 그게 의도다. 라벨을
  되풀이하는 툴팁은 이미 읽은 것의 사본이고, 플레이어는 그 상자를 이제 피해 봐야 한다. 포인터가
  링을 이긴다 — 둘 중 더 최근이고, 마우스로 손을 뻗은 사람은 그 아래 있는 것을 묻고 있다.
  포인터가 아무것도 안 가리키면 키보드가 든 컨트롤이 답한다(마우스로만 닿는 힌트는 절반이 평생
  못 본다). `Widget.state(boolean, boolean)`과 `hovered()`가 열린 것도 이 페이즈다: 서피스는
  위의 `Focus`로 컨트롤에 닿고, 컨트롤은 이걸로 자기 부품에 닿는다.
- `ui/DevScreen` + `ui/DevChrome` — 레일, 마스트헤드, 푸터, F키 바인딩이 한 자리로 모였다.
  `DevScreen.Page`가 레일 순서와 `FullmoonClient.BINDINGS` 순서를 **같은 하나**로 만든다: 레일이
  못 가는 페이지에 닿는 키는 여기 추가해야만 존재할 수 있다. `ringed`는 링을 누가 들고 시작하는지
  하나로 정한다 — F키로 연 페이지는 키보드 주인이 없고 첫 Tab이 레일에 앉는다.
- `ui/ListScreen` — 스위프는 행 인스턴스 **둘**로 8상태 × 2선택 열여섯 칸을 그린다(행은 놓는
  것과 그리는 것이 한 호흡이고, 둘 다 서피스에 등록되지 않으니 아무것도 히트하지 않는다). 우물엔
  이 클라이언트 자신의 컬러 토큰 19개와 패킹된 값이 들어가고, 고른 토큰은 복사 버튼이 클립보드로
  보낸다 — 시연용 더미 데이터를 채우면 우물이 진짜 리스트로 검증되지 않는다.

### 증거

`gradlew -p mod clean build` → `exit=0`(`fullmoon-client-3.0.0.jar`, 2.4 MB),
`gradlew -p mod test` → `exit=0`. 테스트 **136개**, 실패 0 / 에러 0 / 스킵 0, 클래스 14개:

| 클래스 | tests | failures |
| --- | --- | --- |
| `layout.BoxTest` | 10 | 0 |
| `layout.StackTest` | 7 | 0 |
| `text.TypesetTest` | 6 | 0 |
| `ui.StateTest` | 5 | 0 |
| `ui.VoiceTest` | 9 | 0 |
| `ui.FocusTest` | 17 | 0 |
| `ui.SurfaceTest` | 18 | 0 |
| `ui.SliderTest` | 8 | 0 |
| `ui.SelectTest` | 11 | 0 |
| `ui.TextFieldTest` | 17 | 0 |
| `ui.ListRowTest` | 5 | 0 |
| `ui.ListPanelTest` | 10 | 0 |
| `ui.TabRailTest` | 6 | 0 |
| `ui.TooltipTest` | 7 | 0 |

뮤테이션 둘. 먼저 고른 행의 틱 폭을 키보드 것과 같게(`Stroke.FOCUS` → `Stroke.HAIR`) 만들면:

```
136 tests completed, 2 failed

ListRowTest.nothingLooksTheSameChosenAsUnchosen()
    org.opentest4j.AssertionFailedError: FOCUS_VISIBLE ==> expected: not equal but was:
    <Look[ground=-14212066, tick=-667538, tickWidth=1, ink=-1053211]>
ListRowTest.theChosenTickIsWiderThanTheKeyboardsOwn()
    org.opentest4j.AssertionFailedError: expected: <2> but was: <1>
```

열여섯 칸이 열다섯 칸이 되는 지점을 이름까지 대서 잡는다 — 고른 행과 안 고른 행이
`FOCUS_VISIBLE`에서 바닥·틱·잉크가 전부 같아진다. 두 번째는 툴팁의 좌우 클램프를
`within.x() + GAP`으로 되돌린 것이고, 아래 **캡처가 잡은 것**에 그대로 옮겼다. 원본 복원 후
`diff -q` 동일, 재실행 `exit=0`.

레이아웃 산술은 캡처에서 픽셀로 쟀다(1920×1200, gui scale 2 → 화면 좌표 = 픽셀/2).

- 스위프: 섹션 헤드가 본문 첫 줄 gui y 104에서 시작하고 8개 밴드가 **gui y 138부터 24px
  피치**로 330에서 끝난다. 두 열은 gui x 293..515와 519..740(스파인 73, 셀 갭 4). `#362D13`
  바닥이 밴드 0·2·3·5·6·7에 있고 1·4에 없다 — `REST`·`FOCUS`·`DISABLED`·`LOADING`·`ERROR`는
  선택의 워시, `ACTIVE`는 `Voice.QUIET`의 눌린 바닥 자체가 워시라서 여섯 개, `HOVER`는
  `#322F28`, `FOCUS_VISIBLE`은 `#27241E`로 우물 밖으로 들려 있다.
- 틱: 고른 열은 여덟 밴드 전부 gui x **523..525(2px)**, 안 고른 열은 `FOCUS_VISIBLE`과
  `ERROR`에만 gui x **297..298(1px)**. 둘 다 행 왼변 + 4(`space.snug`)에서 시작한다. 색은
  살아있지 않은 상태에서 잉크로 물러난다: `ACTIVE` `#C9A44F`(`accent.pressed`), `DISABLED`
  `#4E4D49`(`ink.disabled`), `LOADING` `#7B7974`(`ink.tertiary`), `ERROR`
  `#D25853`(`status.danger`) — 폭은 2px를 유지한 채로. 꺼진 리스트도 어느 행이 골라졌는지는 안다.
- 우물: 박스 gui y 424..546(`heightFor(5)` = 122), 뷰포트/트랙 425..545(120), 레일은 gui x
  735..739(4px = `space.snug`)로 오른쪽 테두리에 붙는다. 썸은 **31px** = 120 × 5 ÷ 19이고,
  안 굴린 상태에서 425..456, 세 행 굴린 상태에서 444..475 — `425 + (120 − 31) × 3 ÷ 14 = 444`.
  네 숫자가 다 공식에 떨어진다.
- 레일: 열린 탭의 액센트 밑줄이 gui y **86..88(2px)**, 화살표가 예행한 탭의 `line.strong`
  표시가 gui y **87..88(1px)** — 아랫변을 공유하고 무게가 절반이다. 레일 자신의 강한 규칙선은
  gui y 92..93.
- 마스트헤드: 액센트 바가 `p1d-specimen-960x600.png`과 `p1d-list-960x600.png` 둘 다에서 gui x
  220..222, y **14..31**. `capTop(DISPLAY, 24)` = 14, `capHeight(DISPLAY)` = 17 — P1-B가 고친
  캡 밴드 계산이고, 이제 두 페이지가 같은 한 줄에서 나온다.
- 힌트 상자 셋: 레일 힌트 x = **220** = `content.x()`, 우물 힌트 x = 220, 복사 버튼 힌트
  오른변 = **736** = `content.right()`(740) − 갭 4. 우물 힌트는 y 399..420으로 **위로 뒤집혀**
  있다 — 아래는 548..570이고 `footerY(600)`이 565다.

캡처 여섯 장은 [evidence/README.md](evidence/README.md)의 P1-D 표.

### 캡처가 잡은 것

- **힌트가 열 밖으로 나갔다.** 복사 버튼 힌트가 gui x 689..862였고 본문 열은 740에서 끝난다 —
  블러 처리된 페이지 바깥으로 172px이 걸려 있었다. 툴팁이 창을 기준으로 클램프하고 있었기
  때문이다. 서피스가 넘겨주는 **영역**을 받게 고쳤고, 이제 오른변이 736 = 740 − 4다.
- **힌트가 푸터를 덮었다.** 우물 힌트가 gui y 548..570으로 앉았고 `footerY(600)`이 565다 —
  나가는 방법이 적힌 줄을 가리고 있었다. 아래에 자리가 없으면 위로 뒤집게 고쳤고, 이제 399..420이다.
- **마스트헤드가 탭 사이에서 움직였다.** `SpecimenScreen`이 들고 있던 사본은 액센트 바를 P1-B가
  고치기 전 계산으로 그리고 있어서, 탭을 옮기면 같은 자리에 있어야 할 바가 50px 뛰었다. 한 장만
  보면 안 보이고 두 탭 캡처를 번갈아 봐야 보인다. `DevChrome`으로 옮긴 뒤 두 페이지에서 같은
  gui x 220..222, y 14..31이다.
- **내 수정이 4px 어긋남을 넣었다.** 영역 클램프를 `within.x() + GAP`으로 잡았더니, 열 왼변에
  붙은 컨트롤의 힌트가 전부 220에서 224로 밀렸다 — 섹션 헤드와 행과 마스트헤드가 다 맞춰진 그
  선에서 혼자 떨어져 나오고, `rail.png`에서는 힌트가 섹션 헤드의 액센트 틱(gui 220, 105..112)을
  덮었다. 좌우 변엔 규칙선이 없으니 갭이 벌어 주는 것도 없다. p1d2 → p1d3 프레임 픽셀 비교로만
  잡혔고, 클램프를 영역 변 그대로로 되돌린 뒤 테스트를 하나 더 얹었다. 그 테스트를 물게 하는
  뮤테이션:

```
7 tests completed, 4 failed

aHintTooWideForTheScreenStaysAtTheLeftEdge()
    org.opentest4j.AssertionFailedError: expected: <0> but was: <4>
aControlOnTheRegionsLeftEdgeKeepsItsAlignment()
    org.opentest4j.AssertionFailedError: expected: <220> but was: <224>
slidesAlongTheEdgeRatherThanOffIt()
    org.opentest4j.AssertionFailedError: expected: <320> but was: <316>
theRegionsEdgesAreTheOnesThatCount()
    org.opentest4j.AssertionFailedError: and pulled back inside the column ==> expected: <280> but was: <276>
```

프레임 비교는 수정이 그것만 건드렸다는 것도 같이 증명했다. p1d3 → p1d4에서 `kit.png`/`list.png`는
바이트 단위로 동일하고, 나머지는 자기 힌트 상자 안에서만 다르고, `specimen.png`만 gui x
526.5..601.5 / y 289.5..296에서 209px 다르다 — 그 페이지가 살아있는 표 형식 숫자 카운터를
띄우고 있는 자리다.

### 아직 아닌 것

- 고른 행에서 `HOVER`와 `FOCUS_VISIBLE`은 **바닥 하나만** 다르다(`#322F28` vs `#27241E`). 둘 다
  넓은 선택 틱을 쓰고 있어서다. 패널의 링이 "키보드가 이 리스트에 있다"를, 틱이 "어느 행인지"를
  말하니 화면에서는 갈리지만, 행 단위로 놓고 보면 이 한 쌍은 정직하게 얄팍하다. 숨기지 않고 적어
  둔다.
- 스페시멘 페이지의 오른쪽 끝이 들쭉날쭉하다. 본문이 420px인데 프레임이 520px이라 100px이 남는다.
  탭 사이 크롬이 안 흔들리는 값이 한 페이지의 오른변 대칭보다 크다고 보고 그대로 뒀다 — 스페시멘에
  두 번째 열을 주는 건 P2다.
- 우물 힌트는 위로 뒤집힌 뒤 자기가 설명하는 리스트의 섹션 헤드를 덮는다. 지나가는 상자이고
  푸터를 넘는 것보다 낫지만, 제대로 된 답은 위아래만이 아니라 **옆으로도** 갈 수 있는 힌트다.
  지연과 페이드가 없는 것도 같은 줄에 있다: 둘 다 모션이고, 모션은 그걸 설명하는 토큰과 함께 온다.
- 휠은 `ListPanel`만 받는다. P1-C에서 P1-D로 넘긴 결정이고, **슬라이더엔 안 붙이기로** 했다:
  스크롤되는 패널 안의 슬라이더가 휠을 먹으면 리스트가 멈추고, 플레이어는 자기가 방금 무슨 값을
  바꿨는지 모른다. 값 조절은 화살표와 드래그로 충분하다.
- 서버가 있는 서피스는 아직 없다. 여기까지는 전부 오프라인 클라이언트 한 대에서 찍었고, 실제
  월드 위에 뜨는 표면과 HUD는 P2·P3다.

## 2026-08-29 · P2 인게임 서피스 (설정, 단축키 편집 및 충돌 감지, 모드 브라우저, 계정)

인게임에서 실제로 동작하는 4개 핵심 서피스(설정, 단축키, 모드, 계정)를 구현하고, 상단 `TabRail`을 통해 단일 허브로 통합했다. 모든 서피스는 `SurfaceScreen` 기반으로 단일 이벤트 파이프라인을 통과하며, 블러 처리된 스트라텀(`painter.blurredStratum()`)과 토큰 기반 디자인 시스템을 준수한다.

- `ui/SurfaceScreen` — 마인크래프트 화면 입력을 `Surface` 이벤트(`press`, `release`, `pointer`, `scroll`, `key`, `type`)로 직접 변환하는 기본 추상 스크린.
- `settings/SettingsScreen` — 검색 및 즉시 옵션 바인딩을 지원하는 마스터-디테일 설정 장부. `SettingSearch`로 NFKC 정규화 및 다국어 다중 단어 검색 지원.
- `keybinds/KeybindsScreen` & `KeybindConflict` — 바닐라 및 클라이언트 키 바인딩 목록 표시, 충돌 실시간 감지(`status.danger` 및 경고 표시), 키 입력 대기(`listening`) 및 즉시 저장, 기본값 복원 기능.
- `mods/ModsScreen` & `ModSearch` — `FabricLoader`로부터 활성 모드 목록 및 메타데이터(버전, 제작자, 설명, 환경)를 추출하여 표시하는 모드 브라우저.
- `account/AccountScreen` — 플레이어 프로필(이름, UUID, 계정 유형), 서버 연결 상태(IP, 라이브 인디케이터 핑 점), UUID 및 서버 주소 클립보드 복사 기능.
- 상단 통합 탭 레일: `설정` · `단축키` · `모드` · `계정` 4개 서피스가 단일 탭 레일로 매끄럽게 전환.
- `ko_kr.json`, `en_us.json` 로컬라이징 완비 및 `Tokens.java` / `verify-tokens.mjs` 64개 파일 무결성 통과.

### 증거

`gradlew -p i3/mod clean build test --console=plain` → `BUILD SUCCESSFUL`. 테스트 **152개**, 실패 0 / 에러 0 / 스킵 0, 클래스 17개:

| 클래스 | tests | failures |
| --- | --- | --- |
| `keybinds.KeybindTest` | 4 | 0 |
| `layout.BoxTest` | 10 | 0 |
| `layout.StackTest` | 7 | 0 |
| `mods.ModSearchTest` | 1 | 0 |
| `settings.SettingSearchTest` | 8 | 0 |
| `text.TypesetTest` | 6 | 0 |
| `ui.FocusTest` | 17 | 0 |
| `ui.ListPanelTest` | 12 | 0 |
| `ui.ListRowTest` | 6 | 0 |
| `ui.SelectTest` | 11 | 0 |
| `ui.SliderTest` | 8 | 0 |
| `ui.StateTest` | 5 | 0 |
| `ui.SurfaceTest` | 18 | 0 |
| `ui.TabRailTest` | 6 | 0 |
| `ui.TextFieldTest` | 17 | 0 |
| `ui.TooltipTest` | 7 | 0 |
| `ui.VoiceTest` | 9 | 0 |

## 2026-08-29 · P3 HUD 및 4px 그리드 스냅 HUD 에디터

인게임 오버레이로 실시간 정보를 전달하는 HUD 시스템과 드래그 앤 드롭으로 자유롭게 배치 가능한 HUD 에디터를 구현했다.

- `hud/Anchor` — 9개 화면 기준점(`TOP_LEFT` ~ `BOTTOM_RIGHT`) 기반 해상도 독립 배치 시스템. 해상도나 GUI 배율이 바뀌어도 기준점과의 거리(offset)를 정확히 보존.
- `hud/HudElement` & `BaseHudElement` — 통일된 다크 글래스 음각 칩 스타일, 베이크드 폰트 타이포그래피, 라이브 상태 점을 갖는 HUD 모듈 인터페이스.
- `hud/CoordinatesHud`, `FpsHud`, `PingHud`, `ClockHud`, `KeystrokesHud` — 실시간 인게임 좌표/방향, FPS, 레이턴시, 시계, WASD/마우스 키스트로크 모듈.
- `hud/HudConfig` & `HudElementRegistry` — `config/fullmoon/hud.json` 포맷의 GSON 직렬화/역직렬화 및 런타임 상태 관리.
- `hud/HudEditorScreen` — 마우스 드래그로 위치 이동, 4px 그리드 자동 스냅, 선택된 모듈 외곽에 골드 `ACCENT` L자형 코너 틱(L-shaped corner ticks) 표시, 툴바 토글 및 즉시 저장/초기화.
- `hud/HudOverlay` — Fabric API `HudElementRegistry`를 통한 무간섭 인게임 렌더링 파이프라인.

### 증거

- `gradlew -p i3/mod test` → **158개** 테스트 전원 통과 (새로운 `AnchorTest`, `HudConfigTest` 포함).
- `node i3/design/verify-tokens.mjs` → 82개 파일 무결성(토큰 리터럴 0건 위반) 통과.
- `docs/evidence/p3-hud-editor-960x540.png` — 헤더 3단 분리, 4px 스냅 가이드, 골드 L자형 코너 틱, 광학 수직 중앙 정렬(`Typeset.centred`), 플로팅 키캡 키스트로크 모듈의 시각 검증 완료.

## 2026-08-29 · P4 런처 코어 (Rust / Tauri v2 기반 인스톨러 및 런처 파이프라인)

Tauri v2 백엔드 상에서 모장 버전 매니페스트 파싱, 자산/라이브러리 SHA1 무결성 검증, 패브릭 로더 설치, 오프라인 및 Microsoft 인증 프로필, JVM 인자 및 클래스패스 합성, 프로세스 스폰 및 실시간 로그 스트리밍 엔진을 구축했다.

- `download.rs` — 파일별 SHA1 해시 스트리밍 무결성 검증, 깨진 파일 자동 제거 및 재다운로드 방지(`already_good`), `.part` 임시 파일 atomic rename 파이프라인.
- `install.rs` — 공유 캐시 디렉터리(`shared/`) 및 인스턴스 격리 레이아웃, 패브릭 오버레이 프로필 병합, 자산/라이브러리 병렬 다운로드.
- `launch.rs` — OS 및 아키텍처별 규칙 평가, 버전 JSON의 인자 목록(`arguments.jvm`, `arguments.game`) 동적 치환, 메모리 및 GC 인자 합성, 메인 클래스 실행 계획(`Plan`) 생성.

### 증거

- `cargo test --manifest-path launcher/src-tauri/Cargo.toml` → **20개** Rust 단위 테스트 전원 통과 (`download::tests`, `launch::tests`, `auth::tests`, `version::tests`, `ping::tests`).
- `download::tests::already_good_rejects_corrupt_sha1` — 변조/손상된 자산의 SHA1 불일치 감지 및 거부 검증 완료.
- `download::tests::sha1_of_calculates_correct_hash` — SHA1 해시 연산 정확성 검증 완료.
- `launch::tests::plan_composes_jvm_and_game_args_correctly` — JVM 메모리, GC, 클래스패스, 유저네임 인자 합성 검증 완료.

## 2026-08-29 · P5 런처 프론트엔드 UI (Tauri v2 + React 18 디자인 시스템)

동일한 디자인 토큰과 다크 글래스 스트라텀 위에서 동작하는 풀문 런처의 8개 핵심 UI 서피스를 구현하고 전수 검증했다.

- `screens/Home.tsx` — 대시보드 히어로 배너, 초승달 애니메이션(`Moonrise.tsx`), 인스턴스 빠른 접속 칩, 실시간 공지/뉴스 피드, 월렛/코인 거래 내역 장부, 서버 오프라인/온라인 상태 카드.
- `screens/Mods.tsx` — 1st-party 번들 및 활성 모드 브라우저, 카테고리 필터(HUD, 성능, 라이브러리), 모드별 토글 스위치, 로더/호환성 상태 사이드바.
- `screens/Cosmetics.tsx` — Three.js / skinview3d 기반 실시간 3D 플레이어 렌더러, 케이프/날개/궤적 코스메틱 장착 및 걷기/정지 애니메이션 모드 전환.
- `screens/Accounts.tsx` — Microsoft OAuth 디바이스 코드 및 오프라인 플레이어 프로필 관리, 활성 계정 전환, 스킨 미리보기.
- `screens/Settings.tsx` — Java 런타임/메모리 할당 슬라이더, 프라이버시/텔레메트리 스위치, 내장 Web HUD 모듈 레이아웃 시뮬레이터(`widgets/HudEditor.tsx`).
- `components/CommandPalette.tsx` — `Ctrl+K` 전역 핫키를 통한 빠른 네비게이션 및 인스턴스 실행 커맨드 팔레트.
- `widgets/LaunchOverlay.tsx` — 게임 시작 시 출력되는 프로그레스 바, 실시간 표준 출력/에러 로그 스트리밍 콘솔, 숨기기 및 강제 종료 액션.

### 증거

- `node launcher/scripts/verify.mjs` → Headless Chromium 기반 8개 서피스 E2E 여정 전수 통과:
  1. `docs/evidence/fullmoon-launcher-01-home.png` — 대시보드 히어로 & 뉴스 & 월렛
  2. `docs/evidence/fullmoon-launcher-02-mods.png` — 모드 브라우저 & 토글
  3. `docs/evidence/fullmoon-launcher-03-cosmetics.png` — 3D 스킨뷰 & 케이프 피커
  4. `docs/evidence/fullmoon-launcher-04-accounts.png` — 계정 매니저
  5. `docs/evidence/fullmoon-launcher-05-settings-general.png` — 설정 장부 & 메모리 슬라이더
  6. `docs/evidence/fullmoon-launcher-06-settings-hud.png` — 웹 HUD 모듈 시뮬레이터
  7. `docs/evidence/fullmoon-launcher-07-command-palette.png` — `Ctrl+K` 커맨드 팔레트
  8. `docs/evidence/fullmoon-launcher-08-launch-overlay.png` — 게임 실행 & 실시간 로그 스트리밍 오버레이



## 2026-08-30 · P6 versioned Paper channel and truthful server HUD

P6 resumes client work without touching the concurrent launcher worktree. It introduces a
versioned `fullmoon:v1` Fabric custom payload, an immutable bridge state machine, measured Paper
health in the existing TPS chip, and a restrained server notice overlay.

- `network/BridgeProtocol` defines typed hello, welcome, HUD sync, and notice messages. It accepts
  the Minecraft byte-array frame or bare fixture JSON, limits the payload to 32,767 bytes, and
  validates every version, revision, metric, string length, severity, and duration before state
  can change.
- `network/BridgeState` owns the five-second handshake fallback, protocol compatibility, monotonic
  HUD revisions, five-second metric freshness, and expiring notice state through immutable record
  replacement.
- `network/FullmoonChannel` registers the Fabric codecs and lifecycle callbacks, sends the actual
  installed mod version on join, clears state on disconnect, and logs handshake boundaries without
  logging every steady-state metric at info level.
- `hud/ServerTickHud` now displays Paper-owned TPS and tick time. It renders `—` when the channel is
  absent, incompatible, or stale; only the editor retains a labelled demonstration value.
- `hud/ServerNoticeOverlay` fits two lines into a flat, token-only strip with a two-pixel semantic
  severity rule. It adds no icon, gradient, motion, sound, or action.

### Test-first evidence

The first targeted test run was red because the production types did not exist. The compiler
reported, among the expected missing-type errors:

```text
BridgeStateTest.java:114: error: cannot find symbol
symbol:   class BridgeState
BUILD FAILED in 15s
GRADLE_EXIT=1
```

After implementation, the protocol and state suites passed. Edge-case tests cover framed and bare
payloads, malformed JSON, oversized input, fractional and out-of-range numbers, notice bounds,
protocol mismatch, stale metrics, duplicate revisions, notice expiry, and handshake timeout.
JaCoCo verification now enforces at least 80% line and branch coverage over `SettingSearch`,
`BridgeProtocol`, and `BridgeState` rather than reporting coverage without a gate. The final full
run executed 180 tests with 0 failures, errors, or skips. The gated class set reached 97.99% line
coverage and 93.63% branch coverage.

### Executed Paper flow

A temporary Paper bridge fixture on local port 25566 sent the server's measured TPS and average tick
time once per second, then sent notice `p6-roundtrip-dusk`. The client log records:

```text
[12:44:35] [Render thread/INFO] (Fullmoon/Channel) Sent fullmoon:v1 hello (proto 1)
[12:44:36] [Render thread/INFO] (Fullmoon/Channel) Received fullmoon:v1 welcome (server proto 1, mode ACTIVE)
[12:44:37] [Render thread/INFO] (Fullmoon/Channel) Received fullmoon:v1 HUD revision 1 (19.749744571037354 TPS, 3.8701057899999998 ms)
[12:44:46] [Render thread/INFO] (Fullmoon/Channel) Received fullmoon:v1 notice p6-roundtrip-dusk
```

The matching server log records the Fullmoon 3.0.0 hello, HUD revision 1, and the same notice ID.
`docs/evidence/p6-live-channel-960x540.png` shows the resulting notice and `TPS 20.0 · 2.2 ms` in
the real client. `p6-live-channel-320x180.png` verifies the compact GUI width. The fixture held the
overworld at dusk tick 13000 with clear weather during both captures.

A second run used protocol 0 and intentionally sent no welcome. The client remained playable and
recorded the exact fallback boundary:

```text
[12:46:40] [Render thread/INFO] (Fullmoon/Channel) Sent fullmoon:v1 hello (proto 1)
[12:46:45] [Render thread/INFO] (Fullmoon/Channel) No fullmoon:v1 welcome within 5 seconds; using vanilla fallback
```

`docs/evidence/p6-legacy-fallback-960x540.png` shows the user-visible result: no error toast and
`TPS —`. The installed bridge JAR was restored to its pre-test SHA-256
`db30e62c8d1bbed9ba75d87b099caa82055a4c9ce6656001c78cab7d055fc14c`, and the local server was
stopped after capture.

The first ambience fixture incorrectly applied world time to every loaded dimension. Paper rejected
the clockless dimension with this exact exception:

```text
java.lang.IllegalArgumentException: Cannot set time in world without world clock
at org.bukkit.craftbukkit.CraftWorld.setFullTime(CraftWorld.java:813)
```

That capture was discarded. The fixture was narrowed to the overworld, restarted without the task
exception, and all three committed frames were captured from the corrected run.

### Runtime noise observed

The offline development client still emitted these unrelated Microsoft/Realms authentication
failures while loading the local world:

```text
Could not authorize you against Realms server: java.lang.RuntimeException: Failed to parse into SignedJWT: FabricMC
Failed to retrieve profile key pair
com.mojang.authlib.exceptions.MinecraftClientHttpException: Status: 401
```

The server also emitted this unrelated lobby-build warning:

```text
[LobbyMotion] fast-travel pad is not standable: moon_room
```

Neither error interrupted the local join, protocol exchange, HUD update, notice display, or legacy
fallback. The full Hallmark audit and contrast evidence are in
`docs/evidence/p6-hallmark-audit.md`.

## 2026-08-30 · P7 native server route ledger

P7 completes the client half of the existing `fullmoon:v1` warp contract without modifying the
concurrent launcher worktree or the production Paper source.

- `BridgeProtocol` now validates complete waypoint snapshots, rejects duplicate or malformed route
  IDs, bounds coordinates and copy, decodes `waypoint_sync`, `tp_result`, and `screen_open`, and
  emits `tp_request` with only the server-owned route ID.
- `BridgeState` immutably replaces route snapshots, admits one known request at a time, applies only
  a matching server result, exposes a bounded outcome, and turns a silent request into a timeout.
- `FullmoonChannel` opens the native route screen only after a compatible handshake, sends requests
  through the registered Fabric payload, refreshes an open screen after a full snapshot update, and
  converts local send failure into the same visible outcome path.
- `WarpScreen` is a token-only master-detail ledger with all live distances, one primary request
  action, a persistent keyboard ring, explicit server-authority copy, and no icons, gradients,
  motion, purple, or fabricated destination description.
- `ListPanel` now bounds a restored scroll origin to the viewport. The live selection capture found
  that selecting row three in a six-row list hid rows one and two even though all six fit; the final
  capture and regression test verify the correction.

### Test-first evidence

The first protocol/state run failed before implementation with the expected missing contract:

```text
BridgeStateTest.java:199: error: cannot find symbol
symbol:   class Waypoint
location: class BridgeProtocol
51 errors
BUILD FAILED in 1s
```

The route ordering/distance test then failed with three missing `WarpRoutes` symbols, and the visual
regression produced two missing `ListPanel.boundedFirst` symbols before their implementations were
added. The final `./gradlew clean check` run executed 193 tests with zero failures, errors, or
skips. The gated protocol, state, search, and route classes reached 94.76% line coverage and 84.75%
branch coverage.

### Executed Paper flow

A temporary Paper fixture used the production bridge source, called its existing
`openWarpScreen` method after handshake, and delayed the production request handler by 80 ticks so
the waiting state could be captured. The client log records:

```text
[15:15:06] [Render thread/INFO] (Fullmoon/Channel) Sent fullmoon:v1 hello (proto 1)
[15:15:06] [Render thread/INFO] (Fullmoon/Channel) Received fullmoon:v1 welcome (server proto 1, mode ACTIVE)
[15:15:07] [Render thread/INFO] (Fullmoon/Channel) Opened fullmoon:v1 warp screen
[15:15:24] [Render thread/INFO] (Fullmoon/Channel) Sent fullmoon:v1 warp request palace_gate
[15:15:28] [Render thread/INFO] (Fullmoon/Channel) Received fullmoon:v1 warp result palace_gate (accepted)
```

The server accepted the same ID and logged `warp Player969 -> palace_gate`. The selected, pending,
accepted, and 640×360 compact frames are committed under `docs/evidence/p7-warp-*.png`. The fixture
held the overworld at dusk tick 13000 with clear weather. The installed bridge was restored to
SHA-256 `db30e62c8d1bbed9ba75d87b099caa82055a4c9ce6656001c78cab7d055fc14c`, and both Paper ports were
closed after capture.

The shipped Paper plugin exposes `openWarpScreen` but currently has no production caller. That
server-side trigger is explicitly unverified and unchanged; this phase verifies the complete
client behavior when the protocol event arrives.

## 2026-08-30 · P8 in-game map

P8 draws the terrain the client already has and the routes the server already published, and refuses
to draw anything else. It adds no packet: `welcome` carries the waypoint snapshot, so the map reads
the same `BridgeState` the route ledger reads.

- `MapViewport` is the whole coordinate contract — five declared survey scales (1, 2, 4, 8, 16 blocks
  a cell), cell/world projection in both directions, cell panning, and cursor-anchored zoom that
  recentres so the block under the pointer keeps its pixel. It rejects a NaN or infinite centre, an
  undeclared scale, and a zero-sized raster.
- `TerrainSnapshot` carries what the sampler found as run-length rows, and reports the fraction of the
  frame that is real. `TerrainSample` pairs it with the fact that a column was outside the client
  cache, so an unmapped cell draws as grid rather than as invented ground.
- `TerrainSampler` reads the client's own loaded chunks through `getMapColor` and has no unit test,
  because it needs a live `ClientLevel`. The committed frames are the only evidence it has, and the
  footer percentage in each one is the claim it makes.
- `MapMarkers` places the published routes into raster cells and, new in this pass, blanks the labels
  that would land on a kept one. Ledger order decides who keeps the name, and the ring stays on every
  marker: a coarse scale costs names, never destinations. The label box is injected as a `LabelBox`
  because `Typeset.width` needs a running client, the same reason `Clipboard` is an interface.
- `WorldNames` matches a server-published world against the dimension the player is standing in, so
  the plane never marks a route belonging to another world.
- `MapScreen` is the master-detail surface: plane on the left, position and route rail on the right,
  arrow panning, wheel and `±` zoom, `R` back to the player. Clicking a rail row centres the plane on
  that route and requests no teleport. A rail too short to list every route now prints `목록 밖 2개`
  instead of truncating in silence.
- `MapCanvas` draws the plane and stays shaped for a HUD minimap, which is not in this phase.

### What the captures corrected

Three findings came from the rendered frames rather than from the tests:

- At `칸당 8블록` three route names overprinted into an unreadable smear. Fixed in pure
  `MapMarkers.declutter`; `MapMarkersTest` covers the blanking order and the empty-label case, and the
  survey frame shows six rings with four names.
- The rail listed as many rows as fit and said nothing about the rest while the heading still counted
  six. It now admits the loss.
- Light ink on a near-white palace wall was a guess. Each label now draws its own plate at the same box
  the collision test uses, which is why the committed frames are this phase's third capture pass.

The regression tests for the first two were written with the fixes, not before them; the frames are
what failed first.

### Gates on the committed tree

```text
./gradlew -p . test jacocoTestCoverageVerification   → 212 tests, 0 failures, 0 errors, 0 skipped
node design/verify-tokens.mjs                        → scanned 104 file(s)
                                                       no colour or motion literals outside the token block
```

The gated core is 95.78% line and 88.50% branch in aggregate. Every gated map class is at 100% of
both: `MapViewport` 32/32 lines and 12/12 branches, `MapMarkers` 22/22 and 24/24, `TerrainSnapshot`
26/26 and 24/24, `TerrainSample` 6/6 and 4/4, `WorldNames` 9/9 and 12/12.

### Executed Paper flow

Two sessions against the local Paper server, one per window size, with the shipped bridge and no
fixture. The client log records the map's own provenance:

```text
[17:26:32] [Render thread/INFO] (Fullmoon/Channel) Received fullmoon:v1 welcome (server proto 1, mode ACTIVE)
[17:27:28] [Render thread/INFO] (Fullmoon/Map) Map open: 226x140 cells at 2 blocks per cell, 6 published route(s) in minecraft:overworld
```

against Paper's `[FullmoonBridge] handshake ok: Player280 (client=fullmoon v3.0.0, proto 1) — 6
waypoint(s)`. The pairing goes further than the count: Paper logged the join at
`([minecraft:overworld]502.5, 72.0, -16.5)`, the map centres on the player when it opens, and the rail
reads `503 -17`.

The zoom pair is arithmetic a reader can redo. The pointer sat at GUI (337, 295), which is cell
(104, 74) of the 226×140 raster; at `칸당 8블록` around centre (499, −20) that cell holds world
(431, 16); one notch in must therefore centre on 431 − (104 − 112.5) × 4 = 465 and
16 − (74 − 69.5) × 4 = −2. `p8-map-zoom-960x540.png` reads `465 -2`.

The six frames are committed under `docs/evidence/p8-map-*.png` with the two log extracts and
`docs/evidence/p8-hallmark-audit.md`. Nothing was installed on the server, so nothing was restored;
Paper was stopped after the second set.

## 2026-08-30 · P9 warp from the map

The map stops being a read-only instrument. The routes it already marked are how a player asks to be
moved, over the request P7 defined — the client still owns no teleport, and the server still answers
with an id, an `ok` and a reason.

This supersedes one sentence of P8: "the map asks for no teleport of its own". P8's captures and its
audit stand as taken. `PLAN.md` answers that sentence in a new phase instead of editing the phase it
was true for, and the three Hallmark gates whose answers change — 15, 17 and 46 — are answered again
in `docs/evidence/p9-hallmark-audit.md`.

- `MapMarkers.at` hit-tests in cell space: the nearest marker inside the radius wins and ledger order
  breaks a tie. Two markers a player cannot separate at `칸당 16블록` have to resolve the same way every
  frame, because a hint that names one route while a click chooses another is a surface lying about
  where the pointer is.
- `WarpRoutes.reasonKey` now holds the denial vocabulary. It was a seven-arm `switch` inside
  `WarpScreen`; the map asks for the same warp over the same channel, and a vocabulary copied into two
  screens is a vocabulary that drifts in one of them. Membership in a `List` rather than a string
  `switch`, because a multi-label string switch compiles to a hashCode lookup plus `equals` chains
  whose false arms are unreachable, and a gated class cannot reach 100% branch through them.
- `MapScreen` grew the pointer half of the contract: a hint naming the marker under the cursor and its
  coordinates, a click that chooses the marker where it stands, a rail row that still centres on what
  it picks, an action band carrying the one confirm action, and `Enter` as its keyboard road.
  `HIT_CELLS` is 2.5 — the outer ring `MapCanvas` draws around a chosen marker — so the target is the
  ring a player can see, not the block under it.
- A choice outlives every viewport move. Panning away from a destination is not changing your mind
  about it, so pan, zoom and `R` no longer clear it; the rail row stays lit and the marker stays ringed
  however far off frame it goes.
- `MapCanvas.draw` takes a `Marks` record instead of a list and a flag, and `raster` and `plot` became
  public, because a surface that hit-tests a ring has to round the way the ring was drawn.
- `fullmoon.map.marker.hint`, `.chosen` and `.chosen.none` in both languages, and the footer legend
  gained `클릭 선택 · Enter 이동 요청`. The status line reads out of `fullmoon.warp.*`, the ledger's own
  namespace, so one server answer never gets two vocabularies.

### What the frames settle

Nothing in these captures corrected the implementation. Three sessions, eight frames, every one right
on the first attempt. What they settle is the part no test in this repo can reach.

The sharpest is an accident of the geometry. `p9-map-chosen-960x540.png` has `별궁 중앙 홀` chosen — a
route at `500 16` — while `중심` still reads `500 -100`. The claim "a click chooses the marker where it
stands, so the plane does not move out from under the cursor" is one frame wide, and that is the frame.
The rail row above it is lit at the same time, which is the other half: a row centres, a marker does
not.

Two facts of the rig shaped how the rest were taken.

- The join position drifts between runs. P8 saw `503 -17` and `507 -36`; these three sessions started
  at `500.5 -35.5`, `501.5 -35.5` and `495.5 -20.5`. A marker pixel derived from wherever the player
  happened to land is not a number a reader can redo, so every run opens the map and clicks rail row 0
  first. From there the plane is centred on `palace_gate` and every marker is arithmetic.
- A loopback warp round trip is under a tenth of a second and the rig's shutter is at least 1.6 s, so
  the in-flight frame is unreachable against the shipped plugin. It is the only frame with a fixture
  behind it, disclosed below.

### Gates on the committed tree

```text
./gradlew -p . clean test jacocoTestCoverageVerification  → 215 tests, 0 failures, 0 errors, 0 skipped
node design/verify-tokens.mjs                             → scanned 104 file(s)
                                                            no colour or motion literals outside the token block
```

The gated core is 95.90% line and 88.92% branch in aggregate. Both classes P9 changed are at 100% of
both: `MapMarkers` 36/36 lines and 34/34 branches, `WarpRoutes` 13/13 and 4/4. The map classes P8 left
at 100% are still there — `MapViewport` 32/32 and 12/12, `TerrainSnapshot` 26/26 and 24/24,
`TerrainSample` 6/6 and 4/4, `WorldNames` 9/9 and 12/12.

`MapScreen` is not gated and has no unit test, the same as P8. Every claim above about the hint, the
click, the band and `Enter` is a claim about the frames.

### Executed Paper flow

Three sessions against the local Paper server on `:25566`. The first two ran the shipped
`FullmoonBridge.jar`; the third ran a fixture, and only the two in-flight frames come from it.

The 960×540 session asked for the same route twice, and Paper answered differently each time:

```text
[18:22:21] [Render thread/INFO] (Fullmoon/Map) Map route chosen: aux_palace at 500 16 in world
[18:22:24] [Render thread/INFO] (Fullmoon/Channel) Sent fullmoon:v1 warp request aux_palace
[18:22:24] [Render thread/INFO] (Fullmoon/Channel) Received fullmoon:v1 warp result aux_palace (accepted)
[18:22:27] [Render thread/INFO] (Fullmoon/Channel) Sent fullmoon:v1 warp request aux_palace
[18:22:27] [Render thread/INFO] (Fullmoon/Channel) Received fullmoon:v1 warp result aux_palace (cooldown)
```

against Paper's own two lines for those two requests:

```text
[18:22:24] [Server thread/INFO]: [FullmoonBridge] warp Player475 -> aux_palace
[18:22:27] [Server thread/INFO]: [FullmoonBridge] warp denied for Player475: cooldown
```

The refusal is the shipped plugin's `COOLDOWN_MS = 4000` and nothing else — two requests three seconds
apart by the client's own clock. `p9-map-denied-960x540.png` is what `WarpRoutes.reasonKey("cooldown")`
renders: `요청 거절 · 재사용 대기 중` in the danger ink, above a `이동 요청` that is still live, because a
cooldown is a wait and not a wall.

The marker pixels are arithmetic, and this is it longhand. At 960×540 GUI px the edge is
`Tokens.Space.SECTION`, so content is (24, 24, 912, 492), the body starts 48 px below it, and the 208 px
rail plus a 24 px gutter leave the plane 680 px wide — whole 3 px cells make it 678×420, the 226×140
the client logged. `MapViewport.project` puts a route at
`(world − centre) / blocksPerCell + (cells − 1) / 2` and `MapCanvas.plot` rounds that to
`origin + round(cell × 3)` from the raster origin (24, 72). Centred on `palace_gate` at `칸당 2블록`,
`aux_palace` at `500 16` is column `(500 − 500) / 2 + 112.5 = 112.5` and row
`(16 − (−100)) / 2 + 69.5 = 127.5`, so GUI (24 + 338, 72 + 383) = **(362, 455)**.
`p9-map-hint-960x540.png` put the pointer there and read `별궁 중앙 홀 · X 500 Z 16` while `만월궁 정문`
was still the chosen route. `palace_keep` at `500 -140` is row `−20 + 69.5 = 49.5`, GUI **(362, 221)**.

The compact session redoes the same arithmetic at another size: edge `Tokens.Space.LOOSE`, raster origin
(12, 60), 148×88 cells, which puts `palace_keep` at column `73.5` and row `23.5` — GUI **(233, 131)**.
`p9-map-compact-640x360.png` read `만월궁 대전 · X 500 Z -140` there, with `목록 밖 4개` under the two rail
rows that fit and nothing clipped. Its accepted frame was requested with a bare `Return` while the
pointer sat on the marker and nothing held the keyboard, which is the branch in `keyPressed` that exists
for a player whose hand is on the mouse:

```text
[18:24:00] [Render thread/INFO] (Fullmoon/Map) Map route chosen: palace_keep at 500 -140 in world
[18:24:02] [Render thread/INFO] (Fullmoon/Channel) Sent fullmoon:v1 warp request palace_keep
[18:24:02] [Render thread/INFO] (Fullmoon/Channel) Received fullmoon:v1 warp result palace_keep (accepted)
[18:24:02] [Server thread/INFO]: [FullmoonBridge] warp Player582 -> palace_keep
```

Both accepted frames prove the teleport landed and not merely that a banner drew: the player mark moves
onto the marker that was chosen, `aux_palace` at (362, 455) in the 960 set and `palace_keep` at
(233, 131) in the compact one.

#### The one fixture, and its restore

`p9-map-inflight-960x540.png` and `p9-map-inflight-resolved-960x540.png` come from a temporary Paper
plugin built from the shipped bridge source with one insertion: `handleTpRequest`'s body was renamed
`handleTpRequestNow` and a `runTaskLater(..., 80L)` put in front of it. No decision changed — unknown
id, permission, cooldown, world, chunk warm-up, teleport and result are the production path untouched —
the handler just starts 4 s late, inside the client's `WARP_TIMEOUT_MILLIS` of 5 s. The hold is legible
in the log, and the answer at the end of it is the same `accepted` the live plugin gave:

```text
[18:30:22] [Render thread/INFO] (Fullmoon/Map) Map route chosen: aux_palace at 500 16 in world
[18:30:23] [Render thread/INFO] (Fullmoon/Channel) Sent fullmoon:v1 warp request aux_palace
[18:30:27] [Render thread/INFO] (Fullmoon/Channel) Received fullmoon:v1 warp result aux_palace (accepted)
[18:30:27] [Server thread/INFO]: [FullmoonBridge] warp Player49 -> aux_palace
```

That is why the pair is committed and not just the in-flight shot. The first frame is `서버 응답 대기 중`
in amber with the button in its loading state; the second is the same session four seconds later,
`이동 승인됨` in green with the button live again and the player mark on the aux_palace marker. The
fixture delayed the answer; it did not choose it.

The fixture jar was SHA-256 `1d8a2e6e66341836c56a8fe2ec659c03314151071cb1d7005a808cb83567c376`. After
those two frames the installed bridge was restored to
`db30e62c8d1bbed9ba75d87b099caa82055a4c9ce6656001c78cab7d055fc14c` — byte-identical to the jar that
served the first two sessions, and the same value P7 recorded restoring to — and Paper was stopped over
RCON with no JVM left holding `:25566` or `:25577`. Nothing was installed on the production Oracle host
at any point in this phase.

## 2026-08-30 · MapLayout — the map's decisions move inside the gate

P9 shipped with a sentence attached to it: "`MapScreen` is not gated and has no unit test. Every claim
above about the hint, the click, the band and `Enter` is a claim about the frames." Half of that was a
boundary and half of it was a gap. The boundary is real — a `Screen` reaches `Minecraft.getInstance()`,
a `Painter` and `I18n` in nearly every method, and nothing in this repo can call it without a running
client. The gap was that the screen's *arithmetic* was in there too, and arithmetic is exactly what a
test can hold.

`MapLayout` is that arithmetic, 171 lines and 44 gated ones, at 100% of both counters. `MapScreen` went
from 589 lines to 556 and now holds only what needs a client.

- `MapLayout.of(width, height, actionWidth, actionHeight)` divides the window: the compact threshold,
  the page margin, header and footer heights, the rail width, the plane snapped to whole `CELL_SIZE`
  cells, the action band's height from the type it stacks, and the button's box inside it. `init()` is
  one call and one `request.place(layout.action())`.
- The rail's vertical steps became named positions — `positionHeading`, `centreFact`, `scaleFact`,
  `routesHeading`, `routesTop`, `routeRow(index)` — instead of a `y` cursor that each drawing method
  advanced and returned. `section` no longer returns an `int`, because a heading is not a cursor.
- `routeCapacity`, `visibleRoutes` and `beyond` replace the loop that broke when the next row would
  cross the band. `whatTheRailShowsPlusWhatItAdmitsToIsEveryPublishedRoute` is the invariant the rail's
  `목록 밖 N개` depends on, and it is now a test rather than a frame.
- `cellAt(px, py, snapshot)` is the hit test: off-plane answers empty, and on-plane it measures from
  the raster, not from the slot the raster sits in. `pointerCellsAreMeasuredFromThePlaneNotTheSlot` is
  the one that would have caught an off-by-a-margin, which at `칸당 16블록` is several routes wide.
- `raster` and `plot` moved out of `MapCanvas` into `MapLayout`, and `MapCanvas` calls them. P9 had
  made them public so the screen could round the way the ring was drawn; the rounding rule and the
  geometry that uses it now live together, and there is still exactly one of it.

`MapLayout.NONE` exists because `of(0, 0, …)` is not an empty layout. A window of zero still produces
a 3×3 plane at (12, 60) — `Math.max(CELL_SIZE, …)` guarantees it — so `map().holds(x, y)` answered
`true` for a pixel in the corner before `init()` had run. The all-`Box.EMPTY` sentinel is the pre-`init`
value; nine pixels of live hit area is the kind of thing that is invisible until it is a bug report
about a click that did something on a screen that was not up yet.

`node design/verify-tokens.mjs` failed the first time, on `MapLayoutTest.java:25`: the snapshot fixture
filled its cells with `0xFF000000`. The scanner is right and the fix is the sibling tests' own habit —
`TerrainSnapshot.Cell.unmapped(7)`, the same meaningless small int `TerrainSnapshotTest` and
`TerrainSampleTest` use, because a fixture that needs a snapshot's *dimensions* has no business naming
a colour.

### Gates on the committed tree

```text
./gradlew -p . clean test jacocoTestReport jacocoTestCoverageVerification
  --no-build-cache --rerun-tasks                          → 7 tasks executed, 27 suites,
                                                            232 tests, 0 failures, 0 errors, 0 skipped
node design/verify-tokens.mjs                             → scanned 106 file(s)
                                                            no colour or motion literals outside the token block
node generate.mjs (in design/)                            → all contrast floors met,
                                                            status.warn on surface.base 9.35 : 1 (>= 3)
```

The gated core is 96.18% line and 89.30% branch, up from P9's 95.90% and 88.92%. `MapLayout` is
44/44 lines and 14/14 branches; `MapLayoutTest` is 17 tests. Every other gated class is where P9 left
it — `MapMarkers` 36/36 and 34/34, `MapViewport` 32/32 and 12/12, `TerrainSnapshot` 26/26 and 24/24,
`WorldNames` 9/9 and 12/12, `WarpRoutes` 13/13 and 4/4.

What is still outside the gate is now only client runtime: drawing, `I18n` lookups, terrain sampling
off `Minecraft.getInstance()`, the channel send, and the key and mouse dispatch `Screen` owns. That is
a boundary with a reason, not a gap with an excuse.

### Executed Paper flow

A refactor that claims to preserve behaviour has to be run. Local Paper on `:25566` (non-production),
shipped `FullmoonBridge.jar` at `db30e62c8d1bbed9ba75d87b099caa82055a4c9ce6656001c78cab7d055fc14c` —
no fixture installed, so nothing to restore — and two capture sessions retaking P9's baselines:

```sh
timeout 900 python3 tools/capture.py /tmp/p9r-960 --geometry 1920x1080 --scale 2 --server 127.0.0.1:25566 \
  "hint:wait=8,M,wait=3,move=832x181,click,wait=2,move=362x455" \
  "chosen:click"
timeout 900 python3 tools/capture.py /tmp/p9r-640 --geometry 1280x720 --scale 2 --server 127.0.0.1:25566 \
  "compact:wait=8,M,wait=3,move=554x169,click,wait=2,move=233x131"
```

```text
[19:55:22] (Fullmoon/Map) Map open: 226x140 cells at 2 blocks per cell, 6 published route(s) in minecraft:overworld
[19:55:27] (Fullmoon/Map) Map route chosen: palace_gate at 500 -100 in world
[19:55:32] (Fullmoon/Map) Map route chosen: aux_palace at 500 16 in world
[19:56:49] (Fullmoon/Map) Map open: 148x88 cells at 2 blocks per cell, 6 published route(s) in minecraft:overworld
[19:56:54] (Fullmoon/Map) Map route chosen: palace_gate at 500 -100 in world
```

Those two raster sizes are the claim. 226×140 and 148×88 are the cell counts P8 and P9 logged at the
same two window sizes, which means the extracted `of()` and `raster()` divide the window into the same
plane the shipped code did — and the `Map route chosen:` lines mean the extracted `cellAt` and `plot`
land on the same marker pixels: rail row 0 at GUI (832, 181) chose `palace_gate`, and the click at
(362, 455) chose `aux_palace at 500 16`, the arithmetic P9 wrote out longhand.

The frames agree. `p9r-map-hint-960x540.png` is layout-identical to `p9-map-hint-960x540.png` — same
rail rows, `중심 500 -100`, `축척 칸당 2블록`, `항로 6곳`, the same hint plate `별궁 중앙 홀 · X 500 Z 16`,
the same marker plates and footer legend — and `p9r-map-chosen-960x540.png` still has `별궁 중앙 홀`
chosen with `중심` unmoved. `p9r-map-compact-640x360.png` is the compact branch: origin (12, 60),
148×88, two rail rows and `목록 밖 4개`, nothing clipped. The differences are session facts only —
`불러온 지형 81%` against the baseline's `89%`, and the player mark where Paper placed these runs
(`502.5, 72.0, -15.5` and `503.5, 72.0, -16.5`) rather than P9's. Neither session asks for a teleport:
the extraction moved where a marker is, not what confirming one does, and P9's four warp-state frames
still carry that half. Nothing was installed on the production Oracle host.

## 2026-08-31 · P10 — the launcher and client write one HUD layout

P10 began as wiring that already existed in two commits and ended as a driven seam. The mod watches
`config/fullmoon/hud.json` every 500 ms and the launcher reads and writes the same anchor-and-offset
shape through Rust. The finish work did not add a translator. It removed the old percentage position
model from the editor, kept unknown element IDs, and made every write a complete immutable
`HudConfig` so neither side can leave a half-layout behind.

### What the live client did

Two Fabric sessions ran against local Paper, one at 640×360 GUI px and one at 960×540. In each
session the shared file was edited while the client stayed open. The client recorded four adoptions:

```text
[23:33:13] [Render thread/INFO] (Fullmoon/Hud) Adopted hud.json edited outside the game: 8 element(s), mtime 1788100393325
[23:33:43] [Render thread/INFO] (Fullmoon/Hud) Adopted hud.json edited outside the game: 8 element(s), mtime 1788100423209
[23:41:48] [Render thread/INFO] (Fullmoon/Hud) Adopted hud.json edited outside the game: 8 element(s), mtime 1788100908200
[23:42:19] [Render thread/INFO] (Fullmoon/Hud) Adopted hud.json edited outside the game: 8 element(s), mtime 1788100939219
```

The 640 set moves coordinates from the top-left to `BOTTOM_LEFT (40,44)`, then moves FPS to
`TOP_CENTER (31,16)`. The 960 set carries those edge-relative placements into the larger frame,
switches TPS off and moves the clock to `TOP_RIGHT (63,104)`. The before and after captures visibly
agree with `p10-final-hud.json`; the log lines prove each change was adopted rather than reconstructed
after a restart.

The local capture rig uses an offline development identity, so its logs also contain expected
Microsoft authentication 401 responses. They are unrelated to HUD adoption and are not presented as
a clean online-auth run.

### What the browser run found

The first screenshot was not valid evidence: only the navigation and controls were readable. The
fixed `.game-backdrop` had `z-index: 0`, so it painted above ordinary shell copy. A failing source
contract test required an isolated `.app` stacking context and a negative backdrop layer before the
CSS was changed. The next real Chromium frame showed the full settings content.

The second defect needed interaction rather than inspection. Clicking the coordinate row left focus
on that row, but `ArrowRight` did nothing because only the draggable stage node owned `onKeyDown`.
A failing contract test preceded the fix. The pointer-down path now focuses its node, each ledger row
dispatches the same keyboard handler, and the browser probe records `(16,56) -> (20,56)` with
`activeElement.className === "hud-row-pick"`.

The declared Tauri minimum uncovered a third layout failure. At 1040×680 the two-column editor made
the stage 254×143 and wrapped `640 × 360 GUI px` into a vertical stack. A failing responsive contract
test preceded the 1180 px breakpoint. The verified minimum now has one 510 px column, a 510×287 stage,
vertical content scrolling and no horizontal overflow. The default 1280×820 and wide 1920×1080 runs
also report `documentElement.scrollWidth === clientWidth`.

Two smaller feedback corrections came from the Hallmark pass. Reset no longer raises a redundant
success toast because the restored layout is already visible. Dark tertiary copy changed from
`#6B7490` to `#8B97B6`; the new test measures it against both dark grounds and enforces 4.5:1. The HUD
surface consumes named spacing and client-palette tokens, and the shell keeps one restrained gold
bloom instead of three competing gold, blue and cyan blooms.

### Gates on the finished tree

```text
npm test (launcher)                                           → 32 tests, 0 failures
npm run build (launcher)                                      → TypeScript and Vite pass
cargo test --manifest-path launcher/src-tauri/Cargo.toml      → 27 tests, 0 failures
./gradlew -p i3/mod clean test jacocoTestReport
  jacocoTestCoverageVerification --no-build-cache
  --rerun-tasks                                               → 247 tests, 0 failures, 0 errors, 0 skipped
node i3/design/verify-tokens.mjs                              → scanned 110 files
node i3/design/generate.mjs                                   → all contrast floors met
```

The mod gate is 96.58% line and 90.09% branch. The freshly built runtime jar and the launcher's
bundled `launcher/src-tauri/resources/mods/fullmoon-client.jar` are byte-identical at SHA-256
`d75a577eee556547df932443b738aee317e4405807d79ca8427eb53e68eab3b9`.

The release workflows had retained the pre-move `pinion-mod` directory and an old jar glob. They now
build `i3/mod`, exclude `*-sources.jar`, and copy the newly built runtime jar into the Tauri resources
before packaging. This was inspected and YAML-parsed locally; GitHub Actions, PowerShell packaging and
an installed NSIS artifact were not run on this Linux box.

P10 does not claim wiring that is absent. Cosmetics remain launcher-only previews, zoom and
fullbright do not exist in the mod, CPS exists only inside keystrokes, renderers still ignore
`scale`, and the shipped bridge does not publish `hud_sync`, so TPS remains unfed. Those limits are
documented in the plan and README rather than hidden behind a completed phase label.

## 2026-08-31 · First-tester release hardening

A clean real-core profile used to start with no instances while the interface no longer exposed
instance creation. The launcher now creates one stable `fullmoon-managed` Fabric 26.1.2 instance,
copies the profile memory setting, assigns `play.fullmoon.ink` for quick play, creates the isolated
game and mods directories, and persists the instance before the first window is used. Existing
profiles retain their instances and only discard an interrupted install marker. The bundled real
catalog now contains the Fullmoon lobby instead of the mock core being the only place where a server
existed.

The local real binary was built and launched under Xvfb with a new `FULLMOON_DATA_ROOT`. It remained
alive after startup and wrote this state:

```text
id=fullmoon-managed
versionId=26.1.2
installed=false
quickPlayServer=play.fullmoon.ink
directories=instances/fullmoon-managed/minecraft/mods, shared
```

Pull request CI run `33392965627` built the NSIS package on `windows-latest`, installed it silently
into an empty directory, launched the installed `fullmoon.exe` with a clean data root, read the same
managed instance from `instances.json`, checked the production lobby, and confirmed the process was
still alive. The uploaded `Fullmoon_1.0.0_x64-setup.exe` was 8,973,022 bytes with SHA-256
`51ea4d8c4d568941ecd59713d28a088ac9e1588f515ffc672ca661235968e64a`; the downloaded artifact's hash
matched the smoke result. The installed mod hash was
`32da42163dfd0e5dd3f68b45204b663b5392a56de90209124364fc3f598aa5fa`.

The same clean-install script now gates tag releases before artifact collection. Microsoft OAuth
application registration and Authenticode signing remain external release configuration; neither is
claimed by this run.


## 2026-09-02 · P5-R 런처 UI 전면 개편 — Hallmark 58게이트 정합화

P5 이후 올라간 `game-*` 글래스모피즘 레이어가 기존 토큰 시스템과 충돌하고 있었다. 이번
패스는 미세 조정이 아니라 **한 언어로의 재작성**이다. 무엇보다 먼저, 캡처가 잡은 결정적
결함 하나:

- **백드롭이 크롬을 삼키고 있었다.** `.game-backdrop`(fixed, z-index 0) 아래로 모든
  비포지셔닝 요소 — 탑바 제목과 검색 버튼, 타이틀바 컨트롤, 사이드바 그룹 라벨, 설정
  본문 전체, 코스메틱 슬롯 열, 모드 툴바 — 이 페인트 순서상 밑에 깔려 캡처에서
  증발했다. DOM과 히트 테스트에는 살아 있는 채로. `.titlebar`와 `.shell`을
  `--z-shell` 스태킹 레이어로 올려 해결. 증거는 `05-settings-general.png` 전후 비교:
  전에는 탭 레일만 뜬 빈 하늘, 후에는 Java 런타임 장부 전체.

그 외 이번 패스의 사실들:

- **토큰 일괄 회수**: Chakra 그린/레드(`#68D391`/`#FC8181`), 디스코드 블러플(`#5865F2`),
  Tailwind 네뷸러(`#3B82F6`/`#06B6D4`), 히어로의 그라디언트 텍스트, `transition: all`
  14곳, 미정의 토큰 참조(`--radius-pill`/`--text-1`/`--font-ui`) 전부 제거. `styles/`
  안의 생 hex는 grep 기준 0건 — 유일한 예외는 토큰으로 승격한 `--hud-sim-*`(HUD 에디터
  리허설 지평선)과 데이터로서의 액센트 스와치.
- **백드롭 재설계**: 움직이는 반짝임·인디고/시안 네뷸러·그리드 삭제. 깊은 남색 하늘 +
  단일 골드 문 블룸 + 정지한 별(56개, 크기·휘도 2단). 하늘은 날씨이고 스크린세이버가
  아니다.
- **플레이 화면 재구조**: 가운데 정렬 일변도(게이트 6 auto-fail 형상)를 비대칭 2열
  스테이지로 — 왼쪽에 eyebrow·헤드라인(그라디언트 아닌 단색 액센트)·서브·런치 버튼,
  오른쪽에 달빛 단상 위의 3D 피겨. 달토끼 이스터에그는 호버/포커스 의도 위에서만
  `--ease-out`으로 고개를 낸다. 하단 데크(월드 목록 + 주요 소식)는 유지하되 토큰화.
  Play 내부에 HomeScreen을 통째로 임베드하던 스크롤 구조는 폐기 — 두 화면은 이제 각자의
  라우트에서 한 가지 일만 한다.
- **대시보드 재구조**: 마케팅 히어로("백악의 만월궁…")를 걷어내고 기능적 상태 밴드
  (네트워크 n/3 · 접속자 · 잔액 · 인스턴스) + 조용한 언더라인 탭(서버/재화/소식) +
  마스터/레일 데크. 월렛은 히어로 숫자 + 헤어라인 거래 장부, 뉴스는 타일이 아니라
  장부 행.
- **i18n 회수**: Home/Play의 하드코딩 한국어를 `home.*`/`play.*` 키로 전면 이전,
  `cosmetics.liveRender`, `settings.mcVersion` 추가. 영어 로케일이 더는 한국어를
  렌더하지 않는다(`11-en-*.png`).
- **입력/상태 규율**: `.input` 포커스를 box-shadow에서 outline 링으로(기하 불변),
  disabled는 `opacity .55 + cursor: not-allowed + 네이티브 속성` 3채널, 텔레메트리
  토글의 성공 토스트 삭제(효과가 이미 보이는 자리에 축하를 띄우지 않는다).

### 증거

- `npx tsc --noEmit` → 통과. `npm run build` → `✓ built in 1.31s`.
- `node launcher/scripts/verify.mjs` → 12장 전수 재캡처 통과 (`docs/evidence/fullmoon-launcher-01..08`).
- `node launcher/scripts/_theme-locale.mjs` → 라이트 테마·영어 로케일 4장 (`10-light-*`, `11-en-*`).
- `node launcher/scripts/_viewports.mjs` → 1600/1280/1100px 가로 스크롤 프로브 전부 `no-h-scroll`, 1280×800 폴드 안에 CTA·덱 진입 (`20..23`).
- 58게이트 전수 응답표: `docs/evidence/p5r-hallmark-audit.md`.

## 2026-09-02 · P5-R2 리뷰 수정 4건 + 타이틀바 전면 재설계 (만월 마스트헤드)

리뷰에서 나온 네 가지와, 사용자 판정 "쓰레기"였던 타이틀바를 같이 정리했다.

- **코스메틱 슬롯의 중첩 인터랙티브 해제**: 슬롯 버튼 안의 `role="button"` span(해제)을
  실제 버튼 둘로 분리 — 슬롯 선택(`.cos-slot-main`, `aria-pressed`)과 장착 해제
  (`.cos-unequip`, `aria-label`)이 서로 다른 대상을 갖는다.
- **헤딩 계층 정리**: 탑바 h1 아래 화면별 h3/h4 스킵을 h2로. 플레이 스테이지의 디스플레이
  헤드라인은 h1이 아니라 `p`(문서 헤딩이 아니라 표시 텍스트), featured 카드 제목은 버튼
  안이라 `span`+block. 모달 제목 h3→h2. 죽은 `.section-head`/`.page-actions` 규칙 삭제.
- **지갑 "최근"의 정직화**: 통계와 장부가 같은 창(최근 30건)을 읽도록 일치시키고, 잘릴 때
  카운트 줄이 "최근 30건 · 전체 N건"이라고 말한다 (`home.txWindow`).
- **Skin3D 접근 이름**: `label` prop → 캔버스에 `role="img"` + `aria-label`. 네 서피스
  (플레이/대시보드 레일/계정/코스메틱)가 사용자 이름을 전달.
- **타이틀바 재설계**: 40px 스트립(로고+태그)을 44px 마스트헤드로. 브랜드 셀(깃털 타일 +
  FULLMOON 워드마크 + 버전)은 사이드바 기둥 위에 얹히고 **밑변 헤어라인이 없다** — 크롬이
  한 건물로 읽힌다. 스카이 셀은 순수 드래그 영역이며, 게임이 살아 있을 때만 세션 칩이
  뜬다(상태 점: 시작 중 amber/실행 중 green, 모노 업타임, 클릭 → 오버레이 콘솔 복귀).
  오버레이를 숨겨도 이 줄이 여전히 "게임이 돌고 있다"고 말하는 유일한 자리다.
  윈도우 버튼은 46px 히트면. 버전 칩에서 워드마크가 잘리는 것을 잡으면서 core 정보는
  툴팁으로 옮겼다.
- **타이틀바 재설계 과정에서 스태킹 수정 재발 방지**: 재작성한 `.titlebar`에서
  `position/z-index`를 빼먹어 백드롭 버그가 재발할 뻔했다 — `z-shell` 레이어 유수를
  규칙 주석으로 못 박았다.
- 타이틀바의 목 알림("데스크톱 빌드에서 동작합니다")도 `titlebar.desktopOnly` 키로 i18n 회수.

### 증거

- `npx tsc --noEmit` 통과, `npm run build` → `✓ built in 3.15s`.
- `docs/evidence/evidence-titlebar-idle.png` / `evidence-titlebar-session.png` — 타이틀바
  48px 크롭: 대기(마스트헤드+윈도우 컨트롤)와 라이브(세션 칩).
- `fullmoon-launcher-*.png` 20장 최종 상태 재캡처 (01..08, 10 라이트, 11 영어, 20..23 뷰포트).
- `verify.mjs` / `_theme-locale.mjs` / `_viewports.mjs` 전부 통과, h-scroll 프로브 음성.
- 리뷰 게이트 수정 1건: 거래 창의 정렬 가정을 로컬로 강제 — 바인딩 계약은 순서를
  못 박지 않으므로 `at` 내림차순 정렬 후 30건 슬라이스(ISO-8601 UTC 문자열 비교).
  `tsc`/`build`/`verify.mjs` 재통과, 지갑 캡처 갱신.

## 2026-09-02 · P5-R3 성능·모션·디스코드 연동 (피드 + 지갑 읽기), 출석 설계

- **성능**: 원인은 CSS가 아니었다(blur/blend/drop-shadow 잔존 0건 확인). 주범은
  skinview3d — 정지 피겨도 뷰어마다 표시 주사율 rAF를 돌린다. `Skin3D` 재작성:
  정적 피겨(rotate=false·walk=false)는 웨이크 후 1.4초 뒤 `renderPaused=true`,
  포인터 상호작용·케이프 교체·가시성 복귀 시에만 재가동, DPR 1.75 상한. 자동
  회전은 플레이 스테이지 피겨만.
- **모션**: transform/opacity 전용, 토큰 듀레이션 — 대시보드/플레이 `.stagger`
  입장(280ms·12px·40ms 스텝), 탑바 제목 화면 전환 페이드(`key={screen}`),
  세션 칩 scale-in. `prefers-reduced-motion` 전역 규칙이 전부 커버.
- **패치노트 ↔ 디스코드**: 코어 `news.rs` 신설 —
  `play.fullmoon.ink/feeds/patchnotes.json`을 3초 타임아웃으로 fetch → 실패 시
  디스크 캐시 → 번들 카탈로그(빈 패널 금지). `NewsItem.url` 추가(디스코드 메시지
  링크) — 소식 행/피처드 카드/벨 메뉴에서 DISCORD 배지와 함께 `openExternal`.
  발행 절차(봇 → 웹 루트 JSON)는 `docs/patchnotes-feed.md` 계약으로 문서화.
  봇 쪽 발행기는 박스 작업으로 후속.
- **지갑 실데이터 (읽기)**: 실코어에 economy 커맨드가 아예 없어 Tauri 앱에서 지갑이
  항상 비어 있었다. economy-api에 `GET /v1/accounts/by-mc/:username`(읽기 전용
  기존 인증 벽 동일) 신설 — 라벨은 봇의 `cardTheme.txLabel`을 임포트(포크 금지).
  러스트 `economy.rs` + `economy_wallet/transactions` 커맨드 등록. 자격은 앱 데이터
  `economy.json` 파일(baseUrl+읽기 키) — 바이너리에 키를 싣지 않는다. 가짜 풀
  테스트 3건 포함 16/16 통과.
- **출석 클레임**: economy-api의 2026-07-12 읽기 전용 아키텍처 결정과 충돌하므로
  코드가 아니라 설계로 제출 — `docs/economy-claim-design.md`. 핵심: MC 세션
  증명(join/hasJoined) 인증, `ref_id=daily:<UTC날짜>` 공유로 두 표면이 하루
  한 번을 자동 공유, 지급 로직은 봇의 `grant`를 순수 모듈로 추출해 공유(포크
  금지), `ECONOMY_WRITE_MODE` 플래그 기본 off. **승인 후 구현 착수**.
- 기타: `tauri.conf.json` devUrl 4173→5921 수정(깨진 개발 명령 복구).

### 증거

- `npx tsc --noEmit` 통과, `npm run build` 통과, `cargo check` 통과.
- economy-api `node --test test/api.test.js` → 16/16 (by-mc 3건 신규 포함).
- `verify.mjs` 12장 전수 통과 — 소식 탭 DISCORD 배지 확인
  (`fullmoon-launcher-01d-dashboard-news.png`).
- Tauri 앱 실동작: `tauri dev` 재실행, 컴파일 11.7초 클린.

## 2026-09-02 · P5-R4 economy-api by-mc 라우트 프로덕션 배포

런처의 지갑 읽기가 프로덕션에서 동작하려면 economy-api의 by-mc 라우트가 박스에 있어야
한다. 배포·검증 증거:

- 박스 실태: economy-api 유닛은 `/opt/mc-network/minecraft-server-project/economy-api`
  에서 `node src/index.js` (node v22.23.1). 상대 임포트
  `../../coin-bridge-bot/src/economy/cardTheme.js`는 같은 프로젝트 트리의 봇 카피를
  가리킨다.
- **배포 전 발견**: 박스의 cardTheme.js에는 `export function txLabel`이 없다 — 박스
  봇 카피가 더 최신이라 txLabel이 `./txLabel.js`로 추출되고 cardTheme가 **재수출**한다
  (`export { txLabel, txOrigin } from './txLabel.js'`). 즉 import 경로는 그대로
  동작한다. (로컬 coin-bridge-bot 체크아웃이 박스보다 뒤처져 있음 — 별도 동기화
  대상.) streakPolicy.js는 양쪽 md5 동일.
- 절차: 백업(`server.js.bak-20260902-230726`) → scp(server.js + test/api.test.js) →
  재시작 **전** 모듈 로드 스모크(SMOKE OK) → `systemctl --user restart economy-api`
- 검증: 유닛 active · `/v1/health` 200 · by-mc 무키 401(인증 벽) · 실계정
  (seoyeonsama)+키 200 — 잔액 5,603.8534원, 거래 30건, 첫 라벨 "플레이타임"
  (2026-09-02T08:33Z 실시간 데이터).
- 남은 운영자 작업: 런처 배포용 읽기 키 — `ECONOMY_API_CLIENTS`에 `launcher` 클라이언트
  등록(또는 기존 키 재사용 결정) 후, 플레이어 PC의 앱 데이터에 `economy.json`
  { baseUrl: "https://api.fullmoon.ink/economy", token } 배포.
- 로컬 레포의 economy-api 변경은 미커밋 상태 — 커밋·푸시 필요(박스와 정합 유지).

## 2026-09-02 · P5-R5 3D 피겨 철거 — 2D 스프라이트 렌더 (지연 제거)

사용자 보고: "UI가 제깍제깍 따라와야 하는데 딜레이가 있음". 원인 확정 —
skinview3d 뷰어(특히 플레이 스테이지의 autoRotate)가 rAF를 상시 점유해
웹뷰 컴포지터·입력 처리를 밀어낸다. P5-R3의 render-pause는 정적 피겨만 구했고
회전 피겨는 풀 루프 그대로였다. 사용자 제안대로 **3D를 철거하고 2D 정면 렌더로
교체**한다.

- **`Skin2D.tsx` 신설**: 스킨 텍스처의 스프라이트 영역을 CSS 배경으로 배치하는
  정적 렌더 — 캔버스·WebGL·rAF 0. front(얼굴)/back(케이프) 뷰, 16×32 그리드
  ×정수 스케일, `image-rendering: pixelated`. legacy 64×32 스킨은 오버레이
  생략+좌측 다리 미러링으로 처리. 케이프는 뒷뷰에서 **몸 위로 드리움**(먼저
  그리면 10유닛 전체가 몸·팔 뒤에 가려진다 — 첫 캡처가 잡은 결함).
- **4개 서피스 교체**: 플레이(front, scale 11) · 대시보드 레일(front, 7) ·
  계정 히어로(front, 8) · 코스메틱 스테이지(**back**, 10 — 케이프가 보이는
  유일한 뷰). 걷기 토글·드래그 힌트·"실시간 렌더링" 라벨은 2D에서 거짓말이
  되므로 삭제(키 포함).
- **번들**: three.js+skinview3d가 그래프에서 빠지며 index 청크
  780.75 kB(gzip 215.81) → **274.78 kB(gzip 88.02)**, −65%.
  `Skin3D.tsx`/`PlayerRender.tsx`(미사용) 삭제는 승인 대기열 — 승인 후
  `npm uninstall three skinview3d @types/three` 예정.
- **지연 보험 2건**: 화면 전환 `startViewTransition`(전체 창 스냅샷) 제거 —
  CSS 입장 애니메이션으로 충분 · `.game-backdrop`에 `translateZ(0)` 레이어
  승격 — 스크롤이 하늘을 다시 그리지 않게.

### 증거

- `npx tsc --noEmit` 통과, `npm run build` 통과(청크 274.78 kB 확인).
- `verify.mjs` 12장 통과 — `fullmoon-launcher-01-play.png`(2D 정면 피겨),
  `fullmoon-launcher-03-cosmetics.png`(케이프 드레이프 확인).
- Tauri 앱 실동작(HMR로 즉시 반영).
- 리뷰 게이트 수정 3건(Skin2D): ① BACK 뷰 좌표 오류 — 머리 뒷면 (16,8)→(24,8), 몸통 뒷면 (20,32)→(32,20), 오버레이 뒷면들, 왼팔 뒷면 (40,52)→(44,52) 등 표준 64×64 레이아웃으로 전면 교정(기존 좌표는 옆면/타 영역을 잘라냄) ② legacy 미러링 — 오른팔 원본 복사본까지 미러링한 비일관성 제거(왼쪽 복사본만 미러) ③ 죽은 `canvas` 셀렉터 3곳 → `> div` — 지면 그림자가 피겨 발 위에 페인트되던 회귀 수정. 검증: 케이프 미장착 상태의 뒷토르소를 직접 캡처(`evidence-backview-unequipped.png` — 셔츠 뒷면 확인), 장착 상태 드레이프(`evidence-backview-equipped.png`).

## 2026-09-03 · P5-R6 원클릭 셰이더 통합 (feat/easy-shaders 체리포트)

`feat/easy-shaders`의 커밋 5c3123c("one-click Iris and Complementary shaders")를 현재
브랜치로 포팅했다. 작업 트리가 오늘의 대개편으로 지저분하므로 cherry-pick 대신
`git apply --3way`(영향 파일 선스테이징)로 통합하고, 충돌 3건(commands.rs 임포트,
package.json test 스크립트, paths.rs 헬퍼 블록)을 수동 해결했다.

- **기능**: 모드 화면의 "한 번에 설치" → Iris+Sodium+Complementary Reimagined를
  Modrinth에서 해석·다운로드(게임 버전+로더 매칭, sha1 검증) → `config/iris.properties`
  기록으로 다음 실행부터 팩 적용. Iris는 카탈로그에서 기본 off(`defaultEnabled: false`
  + `defaults_seeded` 시딩으로 apply가 되살리지 않음) — 원클릭이 켜는 유일한 경로.
- **통합 메모**: 박스와 로컬의 cardTheme 드리프트와 무관하게 셰이더 경로는 Modrinth
  직접 조회라 영향 없음. paths.rs의 미사용 `MOD_NAMESPACE`/`instance_mod_config_dir`
  (이 브랜치에는 아직 쓰는 곳 없음)는 제거하고 경고 0으로.
- **검증**: `cargo check` 경고 0 · `npm test`(shaders-contract) 2/2 · `tsc` 통과 ·
  `npm run build` 통과 · `verify.mjs` 통과 — `fullmoon-launcher-02-mods.png`에서
  "한 번에 설치" 배너 + Iris 카드(미설치·off) 확인.

## 2026-09-05 · P5-R7 사이트 인터랙션 레이어 + 3D 잔재 철거

P5-R…R6이 디스크에만 있던 상태를 정리하는 세션. 88개 dirty 엔트리와 `git apply --3way`가
남긴 `UU` 3건(`package.json`·`paths.rs`·`commands.rs`)을 읽고 해결해 `6e63136`으로 먼저
체크포인트했고, 그 위에 사이트 슬라이스와 승인 대기 항목을 얹었다.

- **`site/assets/site.js` 신설(192줄)**: 다섯 페이지가 공유하는 단일 스크립트 —
  읽기 진행 바, 스태거 reveal, 같은 페이지 앵커 scrollspy, 코드 블록 복사 버튼,
  커서 스포트라이트, 별밭+히어로 달무리. 전부 `html.js` 게이트 뒤이고
  `prefers-reduced-motion`에서 모션 채널 둘 다 off. reveal은 `transitionend`에서
  자기 클래스(`reveal`/`in`)와 인라인 `transition-delay`를 스스로 제거한다 —
  안 그러면 이후 hover 트랜지션을 하이재크한다(900ms 폴백은 탭 전환 누락 대비).
- **`launcher/scripts/_sitejs.mjs` 신설**: 5페이지 × 1440/768/320px 실측. 스크린샷이
  못 잡는 두 결함을 노린다 — (1) reveal이 안 돌면 DOM은 완벽한데 화면이 `opacity: 0`,
  (2) `html`/`body`의 `overflow-x: clip`이 `scrollWidth`를 눌러서 자식이 뷰포트 밖에
  걸려도 h-scroll 프로브는 깨끗하게 읽는다. 그래서 요소별 `getBoundingClientRect`로
  재고, **조상 중 스크롤 가능한 게 없을 때만** 결함으로 센다(그 walk이 없으면 모바일
  nav 스크롤러와 `<pre>` 안의 하이라이트 span까지 6건 오보한다).
- **결함 1건 실측 후 수정**: `.file-ref`가 320px에서 잘렸다 — 60자 Java 경로를
  줄바꿈 규칙 없는 `inline-block`에 넣어서, clip이 자르고 스크롤로 닿을 방법도 없다.
  `max-width: 100%` + `overflow-wrap: anywhere`(이 파일의 제목 규칙과 같은 관용구).
  달무리는 wrap 밖으로 삐져나오는 게 의도이므로 `data-bleed`로 명시.
- **카피를 트리에 맞춤**(마지막으로 센 시점이 아니라): `commands.rs` 선언·등록
  모두 43개(37 아님) · `src-tauri` 22모듈 5,283줄(19/4,500 아님) · 계약은
  `PinionCore` 41개 메서드이고 `bindings.ts` 스니펫의 시그니처 4개가 전부 틀려서
  파일의 실제 다섯 줄로 교체 · "0개 외부 에셋 다운로드, Mojang 원본만"은 처음부터
  거짓이었고 이 브랜치가 더 키웠다(모드는 Modrinth·Fabric maven, 셰이더는 Iris와
  재배포 금지 팩) → 런처가 설치하는 범위를 규정하는 카탈로그 5개로 교체하고,
  `trust.html`의 설치 후 다운로드 목록을 3종 → 4종으로 고쳐 Iris와 셰이더 팩을 명시.
- **`Skin3D.tsx`/`PlayerRender.tsx` 삭제 + `npm uninstall three skinview3d @types/three`**:
  P5-R5의 승인 대기 항목. 두 파일을 import하는 곳 0건, `three`/`skinview3d`를
  import하는 곳 0건을 먼저 확인했다. `node_modules` 207M → 138M. 번들은 그대로다 —
  청크에서 빠진 건 P5-R5의 import 제거 시점이고(780.75 → 274.78 kB), 이번엔
  설치 용량과 신뢰해야 할 의존성 3개가 줄었다. `docs/evidence/README.md`의
  P5-R2 항목이 가리키던 `Skin3D`의 `label` prop은 `Skin2D`가 그대로 들고 있어
  참조만 고쳤다.

### 증거

- `node scripts/_sitejs.mjs` → `site interaction layer: all clean`, 15/15 조합
  (수정 전 3건 BAD: 768/320px `div.moonglow`, 320px `mod` `span.file-ref` ×4).
- 320px `mod.html` 캡처 — 경로 칩이 컬럼 안에서 3줄로 감기고 우측 끝 296 ≤ 320.
- 카피 수정의 근거를 트리에서 확인: `Ui.MOON`=0xFFF5D06E ↔ `tokens.css --moon-300`
  =#F5D06E, `Ui.INK`=0xFF0B101F ↔ `--bg`=#0B101F(mod.html의 단일 팔레트 주장 성립) ·
  `WarpScreen`이 선택 행에 `Ui.MOON` 2px 규칙을 채우고 푸터가 "서버 응답 대기…" ·
  `BRIDGE.md`에 welcome timeout 5s와 쿨다운 4000ms · `FullmoonBridge`의 페이로드는
  `writeByteArray` 한 번(varint 길이 + JSON 객체 하나).
- 철거 후 게이트: `npx tsc --noEmit` exit 0 · `npm run build` exit 0(index 청크
  279.11 kB) · `shaders-contract` 2/2 · `verify-tokens.mjs` 82파일 리터럴 0.

## 2026-09-06 · 런처 UI 라인과 main 통합

P5-R…R7이 `main`에서 28커밋 뒤처진 브랜치에만 있던 상태를 끝낸다. `origin/main`
(`47784dd`, PR #5 머지)을 `feat/i3-p5-launcher-ui`로 머지했고 충돌 12건이 났다.
해결 정책은 하나 — **main 쪽이 테스트된 계약이나 실제로 실린 마크업을 들고 있으면
main이 이긴다.** 내 쪽이 더 새로워서가 아니라 그게 트리에 체크인된 약속이기 때문이다.

- **`shell.css` + `AtmosphericBackdrop.tsx` → main**: 내 쪽은 블룸 둘과 `.nebula-sky`
  레이어였는데, `scripts/shell-layering.test.ts`가 `nebula-layer` **1개**와
  `.game-backdrop { z-index: -1 }`, `.app { isolation: isolate }`를 정확히 단정한다.
  main의 테스트를 고치는 게 아니라 내 레이어를 뺐다. 안 쓰게 된 `--z-backdrop`
  토큰도 elevation 어휘에서 제거(`--z-shell: 1` … `--z-grain: 999`는 그대로).
- **`screens.css` → main 양쪽 다**: 머지된 `HudEditor.tsx`가 origin/main과
  바이트 동일하고 `hud-plane`·`hud-zone`·`hud-vanilla`·`hud-plane-empty`를 쓴다.
  즉 스타일시트와 마크업 둘 다 main 것이어야 짝이 맞는다.
- **`paths.rs`**: main의 `MOD_NAMESPACE`/`instance_mod_config_dir` 헬퍼를 살리고
  (P5-R6에서 "쓰는 곳 없어서" 지웠던 그 코드가 main에서는 쓰인다) 셰이더 경로
  둘(`instance_shaderpacks_dir`, `instance_iris_properties`)만 그 옆에 접었다.
  HUD·코스메틱 파일은 main대로 `config/fullmoon/`로 간다.
- **`site/launcher.html`·`site/trust.html` → main의 해요체 + 내가 고친 수치**:
  P5-R7에서 센 41개 메서드와 4종 다운로드 목록을 main의 목소리에 실었다.
- **양쪽이 추가만 한 곳은 합집합**: `package.json` 테스트 7개(전부 디스크에 존재
  확인), `main.rs`의 `mod news` + `mod offline`, `commands.rs` import(`store`는
  이미 다음 줄에 있어서 넣으면 중복이었다), `Accounts.tsx` import(`Skin2D` 259행,
  `LOCAL_TEST_USERNAME` 395·409행에서 실제 사용, 미사용 `Empty`는 버림),
  `DEVLOG.md`와 `evidence/README.md`는 각 파일의 정렬 순서대로.
- **main이 참조하지만 정의된 적 없는 이름 2개**: `--raised` → `--surface`,
  `--status-warn` → `--warning`. 머지가 깨뜨린 게 아니라 origin/main의
  `screens.css`가 처음부터 그랬다 — 클래스·변수 전수 감사에서 잡혔다.

### 남은 계약 불일치 (이 머지가 만든 게 아니라, 이 머지로 보이게 된 것)

`i3/mod`(`dev.fullmoon.client`, id `fullmoon`)는 `ci.yml`·`release.yml` 어디에도
없다. 빌드·배포되는 건 `pinion-mod`(`dev.pinion`, id `pinion`) 하나뿐이다.
그런데 main의 `paths.rs`는 런처가 쓰는 `hud.json`·`cosmetics.json`을
`config/fullmoon/`에 두는데, 그 경로를 읽는 쪽은 `i3/mod`다. 실제로 실리는
`pinion-mod`는 게임 디렉터리 기준 `pinion/hud.json`을 읽는다. **계약의 두 끝이
만나지 않는다** — 런처가 쓴 HUD 레이아웃은 배포되는 모드가 열지 않는다.
어느 쪽으로 맞출지(CI에 `i3/mod`를 넣을지, `paths.rs`를 `pinion`으로 돌릴지)는
모드 하나로 합칠지와 같은 결정이라 사용자 몫으로 남긴다.

### 증거

- `npx tsc --noEmit` exit 0 · `cargo check` exit 0 (13.53s, 경고 0).
- 계약 테스트 7파일 **40 pass / 0 fail** — `shell-layering`의 단정 둘 포함.
- `npx vite build` exit 0 (76 모듈) · `verify-tokens.mjs` 110파일 리터럴 0 ·
  `./gradlew -p i3/mod --offline test jacocoTestCoverageVerification` BUILD SUCCESSFUL.
- `node launcher/scripts/_sitejs.mjs` → 15/15 ok, `site interaction layer: all clean`.
- `node scripts/verify.mjs` 라이브 → 14샷,
  `FULLMOON LAUNCHER VERIFICATION COMPLETED SUCCESSFULLY`. 해결한 충돌을 덮는 4장을
  `i3/docs/evidence/merge-*.png`로 남겼다 — 백드롭 레이어링, HUD 에디터, `--surface`,
  `--warning`.

## 2026-09-12 · 모장 앱 ID 승인 확인 — 클라이언트 개발 마무리

모장에서 앱 ID 리뷰 완료 메일이 왔는데 승인 여부를 적지 않아, 추측 대신 라이브 체인으로
판정했다. `launcher/scripts/check-app-registration.mjs`가 기기 코드 로그인 뒤 런처와 같은
순서(MSA → Xbox Live → XSTS → `login_with_xbox` → `profile`)를 그대로 걸어 각 단계의 HTTP
상태만 찍는다. 토큰은 출력에도 디스크에도 남지 않는다.

- 판정: `f5ccd6e4-9e7b-411f-ab67-21e8202adb93`은 Mojang allowlist에 있다. MSA 토큰 수신 →
  Xbox Live 200 → XSTS 200 → `login_with_xbox` **200**(토큰 수신) → `profile` **200**
  (`Person1010`). 이 절차에는 대시보드가 없어서 이 호출이 유일한 판정 수단이다.
- 신청 경로 실측: `aka.ms/mce-reviewappid` → 신청 폼(200), `aka.ms/AppRegInfo` → 301 →
  Minecraft Help 문서(16254801392141).
- 문서: `docs/MICROSOFT-AUTH.md`의 "Mojang 수락 필요 · 지원팀에 확인" 서술을 위 실측으로
  교체했고, `PLAN.md` P4의 "빈 `msClientId`라 미검증" 문장을 해소했다.
- 결정: 런처 인증의 마지막 외부 게이트가 닫혔다. **이로써 클라이언트 개발을 마무리한다.**
- 남은 것(사용자 결정 대기, 이번 결정에 포함되지 않음): `i3/mod`↔`pinion-mod` 계약 불일치
  (2026-09-06 항목), `docs/economy-claim-design.md` 승인 대기, 릴리스·배포 경로.
