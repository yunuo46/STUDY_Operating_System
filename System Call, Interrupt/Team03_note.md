# **시스템 콜이 무엇인지 설명해 주세요.**

시스템 호출은 커널에게 권한이 필요한 작업을 수행하도록 요청하는 것입니다. 예를 들어, 파일을 읽거나 쓰기, 네트워크 통신, 새로운 프로세스 생성과 같은 중요한 작업들은 시스템의 안정성 때문에 커널 모드에서만 실행할 수 있습니다. 시스템 콜은 이러한 privileged 작업이 필요할 때, 사용자 모드에서 커널 모드로 안전하게 전환하여 요청을 전달하는 역할을 합니다.

시스템 콜 규약은 OS ABI의 일부입니다. 규약에서는 시스템 콜 번호를 어느 레지스터에 넣을지, 매개변수를 어떤 순서로 어디(레지스터, 스택, 블 등)에 담아 전달할지, 시스템 콜 완료 후 반환 값을 어느 레지스터에서 가져올지 등을 정의합니다.

## 우리가 사용하는 시스템 콜의 예시를 들어주세요.
## 시스템 콜이, 운영체제에서 어떤 과정으로 실행되는지 설명해 주세요.

  **리눅스에서는 트랩(시스템 콜)도 인터럽트의 일종으로 처리한다.**

  ### x86-32(Legacy)

    - 동작 과정
        1. user process에서 fork() 동작을 호출한다
        2. libc 라이브러리에서 구현한 fork()함수가 sw interrupt를 발생시킨다
            1. 필요한 시스템 콜 번호를 eax 레지스터에 저장한다
            2. int $x80 명령어를 실행하여 인터럽트를 발생시킨다.
        3. IDTR을 통해 IDT(메모리에 위치)의 주소를 알아낸다. - memory access!!
            1. IDTR은 CPU 내부 레지스터
            2. LIDT 어셈블리 명령어를 통해 동적으로 메모리에 IDT의 주소가 올라감, 가장 최근에 로드한 IDT를 사용
        4. IDT(Interrupt Descriptor Table)를 통해 system_call()함수를 찾는다 (여기부터 커널 메모리 & 스택 전환)
            1. 인터럽트가 발생했을 때 각 인터럽트 번호에 해당하는 ISR의 주소가 저장된 테이블이다
            2. 256개의 entry가 존재한다.
            3. CPL & DPL 비교
            4. TSS(Task State Segment)에 접근해 스택 포인터를 로드함
            5. 스택 전환!
            6.  CPU는 인터럽트 발생 시의 사용자 프로그램 상태를 커널 스택에 순서대로 푸시
                1. 기존 사용자 스택의 정보(사용자 스택 포인터 ESP와 스택 세그먼트 셀렉터 SS)
                2. EFLAGS, 코드 세그먼트 레지스터, EIP…
        5. ISR (Interrupt Service Routine)에서 앞서 레지스터에 저장해준 시스템 콜 번호를 통해 시스템 콜 테이블을 참조하여 sys_fork()의 주소를 찾는다.
            1. ISR의 시작에서 SAVE ALL 명령어를 통해 레지스터 상태를 스택에 백업한다.
                1. 범용 레지스터들(EAX, EBX, ECX 등)을 커널 스택에 추가로 푸시한다.
        6. 실제 구현(sys_fork())
            1. 부모 프로세스를 복사하여 자식 프로세스를 만드는 매우 복잡한 작업…
        7. user mode 복귀
            1. ret_from_sys_call과 같은 명령어를 통해 백업해두었던 user process의 상태를 복구한다.
            2. EAX 레지스터에 반환값이 저장된다.(여기서는 PID)

  ### x86-64(Modern)

  x86-64 아키텍처는 기존의 소프트웨어 인터럽트(`int $0x80`) 방식의 성능 병목을 해결하기 위해 `syscall`이라는 전용 명령어를 도입했습니다. 이 명령어는 인터럽트와 달리 CPU의 빠른 시스템 콜 경로를 사용하며, 기존의 느린 인터럽트 벡터 테이블(IDT)을 거치지 않습니다.

    1. 사용자 프로세스가 fork()를 호출하면 **glibc** 라이브러리가 **syscall** 명령어를 실행합니다.
        1. MSR(Model Specific register) 레지스터를 사용
    2. 시스템 콜 번호(**fork는 220번**)는 **RAX** 레지스터에 저장됩니다
    3. syscall 명령어를 통해 미리 설정된 커널 시스템 콜 엔트리 함수로 제어가 바로 넘어갑니다.
    4. 시스템 콜 엔트리 함수(entry_SYSCALL_64)에서 system call table을 참조해 실제 커널 함수 실행!!

## 시스템 콜의 유형에 대해 설명해 주세요.
## 운영체제의 Dual Mode 에 대해 설명해 주세요.
## 왜 유저모드와 커널모드를 구분해야 하나요?
## 서로 다른 시스템 콜을 어떻게 구분할 수 있을까요?
## 추가 질문

  ### 커널 스택 vs 유저스택

    - 커널 스택은 프로세스마다 생성된다
    - 모드 전환 시 스택 포인터가 전환됨
    - 커널 스택은 유저모드로 되돌아가기 위한 정보를 저장

  ### IDT vs SCT

    - IDT : 인터럽트의 진입점 매핑
    - SCT : 시스템 콜 번호와 핸들러 함수 매핑

  ### ISR vs entry_SYSCALL_64

    - ISR은 모든 sw인터럽트를 받는 일반적인 진입점
    - entry_SYSCALL_64는 syscall 명령어에 대한 특화된 진입점

  ### Why would you preserve register values across function calls?

  호출하는 함수(caller)가 중요한 데이터를 잃어버리지 않게 하기 위해서

  ### Do you think every function in a binary should strictly follow the ABI?
  Why or why not?

  컴파일러는 ABI의 제약이 없는 내부 함수를 더 효율적으로 처리할 수 있습니다. 예를 들어, 컴파일러는 레지스터를 더 자유롭게 사용하여 매개변수를 전달하거나, 레지스터 보존 규칙을 간소화하여 불필요한 스택 저장/복구 작업을 줄일 수 있습니다. 이러한 최적화는 프로그램의 실행 속도를 높여줍니다.

  결론적으로, 프로그램은 경계선(boundary)에서만 ABI를 준수하면 됩니다.

  ### Who is going to invoke main?

  우리가 작성하는 C 코드는 `main` 함수에서 시작한다고 알고 있지만, 실제 실행 파일(`.o` 파일)의 진입점(entry point)은 `_start`라는 이름의 다른 함수입니다. 이 `_start` 함수는 운영체제가 프로그램을 실행할 때 가장 먼저 호출하는 함수입니다!

  **The main function is never invoked in the binary code**

  이는 `main` 함수를 호출하는 코드가 우리가 직접 작성한 `hello.c`의 어셈블리 코드에는 없다는 의미입니다.
  
  - **C 런타임 (`crt0`)**: `main` 함수를 호출하고, `main` 함수가 반환한 값을 받아서 시스템 종료를 처리하는 코드 묶음입니다.
  - **C 라이브러리 (`libc`)의 일부**: 이 C 런타임 코드는 `libc`에 포함되어 있습니다. 즉, 우리가 `gcc`로 컴파일할 때 자동으로 링크되는 `libc` 덕분에 `main` 함수가 호출되는 것입니다.
  - **커널에 의해 호출**: 결국, 운영체제(커널)가 `_start`를 호출하면, 이 `_start` 함수는 `__libc_start_main`을 통해 C 런타임을 시작시키고, 이 C 런타임이 최종적으로 `main` 함수를 호출하는 것입니다.

  결론적으로, C 언어의 `main` 함수가 사실은 **운영체제에게 직접 불러지는 것이 아니라, C 런타임이라는 중간 매개체를 통해 간접적으로 호출되고 종료되는 것입니다.**

---
# **인터럽트가 무엇인지 설명해 주세요.**

비동기적 이벤트를 처리하기 위한 기법

## 인터럽트는 어떻게 처리하나요?

  리눅스는 인터럽트 처리를 위해 `irq_desc`라는 배열 형태의 자료구조를 사용합니다.

    - Status : 인터럽트 라인의 현재 상태
    - Handler : 어떤 PIC(인터럽트 컨트롤러)로부터 요청이 왔는지 확인하는 정보
    - Lock : 멀티 프로세서(SMP) 환경에서 여러 CPU가 동시에 같은 인터럽트 라인에 접근하여 충돌하는 것을 막기 위한 상호 배제(mutual exclusion) 기능
    - Action : 실제 인터럽트를 처리할 ISR(인터럽트 서비스 루틴)들의 리스트(포인터 변수)

  IRQ = Interrupt ReQuest

  ### 동작 예시

    1. 디바이스에서 인터럽트 요청이 오면 PIC(Programmable Interrupt Controller)가 이를 관리하여 실제로 CPU에게 보낸다
        1. PIC에서는 프로그래머가 원하는 대로 interrupt를 masking 할 수 있고 각 interrupt 별로 handler를 두어 원하는 동작을 실행하게 할 수 있다.
        2. Interrupt를 처리하고 있는 동안에는 해당 인터럽트 라인의 PIC는 block 상태이므로 **ISR는 가능한 짧은 시간 내에 실행되고 마쳐야 한다.**
    2. 4개의 CPU중 CPU0가 요청을 받았다고 가정하자. CPU0가 처리를 시작하면 간단한 전처리를 마친 뒤, **C로 작성된 do_IRQ()**함수가 호출된다.
        1. CPU를 선택하는 알고리즘으로 Static과 Dynamic 두 가지가 있다. Static은 table을 정해두는 방식, Dynamic은 priority를 계산하는 방식이다.
    3. do_IRQ()에서 상태 필드를 갱신한 뒤, 어느 IRQ line에서 온건지 확인한다.
        1. 해당 CPU는 interrupt가 발생한 mth IRQ line의 lock을 획득한다.
            1. desc->handler->ack(irq)를 통해 PIC에 ack를 전달
            2. 인터럽트의 status를 초기화하고 IRQ_PENDING으로 Set
            3. 이후 ack를 보내고 나면 status를 IRQ_INPROGRESS로 바꾼다.
        2. ack를 빨리 보내야 다음 인터럽트를 받을 수 있다
    4. IRQ3로 확인했다고 가정하자. 이제 IRQ3→action을 찾아보면서 ISR을 수행한다.
        1. IRQ3의 action에 연결된 모든 handler들을 NULL이 될때까지 실행한다.
            1. 인터럽트 벡터 테이블을 참고해서 ISR에 접근한다
        2. 이상태에서 CPU0가 ISR을 수행할 수 있다.
            1. IRQ를 Handle하기 전, 해당 IRQ라인의 Lock을 해제한다.
            2. critical section인 status access를 벗어났기 때문이다

  ### race condition

  CPU3가 m번째 line에서 handle_IRQ_event() 함수를 통해 ISR을 처리 중임에도 같은 line의 또 다른 device에서 interrupt가 발생되었을 때 m line의 lock이 풀려있는 상태라서 다른 CPU가 PIC에 의해 선정되어 m line의 lock을 획득하고 들어올 수도 있다. 마찬가지로 handle_IRQ_event() 함수를 호출해서 ISR을 처리하려고 하겠지만 먼저 처리하고 있는 CPU와 뒤따라 들어온 CPU가 충돌이 날 수 있다.

  → 뒤늦게 들어온 CPU 입장에서는 line state를 확인하고 PENDING bit을 설정하고 나가면 된다. 왜냐하면 이미 처리 중인 CPU에게 처리할 것이 더 있으니 해당 라인에 요청된 ISR 마치고 오라고 말하는 것과 같기 때문이다. 이미 ISR을 처리 중이던 CPU 입장에서 보면 handle_IRQ_event() 함수를 마치고 돌아왔더니 분명히 없애고 갔던 PENDING bit이 설정되어 있으니 다시 ISR을 처리하러 가면 된다.

  ### SoftIRQ

  만약, ISR이 너무 길다면 soft irq bit을 Set하고 나중에 do_softirq()에서 처리하게 된다. 이 soft irq는 bottom half라고 불린다.
  리눅스 커널의 **지연된 작업 처리 메커니즘**

## Polling 방식에 대해 설명해 주세요.
## HW / SW 인터럽트에 대해 설명해 주세요.
## 동시에 두 개 이상의 인터럽트가 발생하면, 어떻게 처리해야 하나요?