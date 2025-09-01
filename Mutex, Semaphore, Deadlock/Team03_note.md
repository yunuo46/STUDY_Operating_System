# 뮤텍스와 세마포어의 차이점은 무엇인가요?
## Critical Section Problem

Critical Section : 둘 이상의 스레드가 동시에 접근해서는 안되는 공유 자원에 접근하는 코드 영역

임계 구역을 보장하기 위해선 다음 세가지 요구사항을 지켜야한다

- Mutual Exclusion : 상호 배제
- Progress : 임계 구역에서 어떤 프로세스도 실행 중이 아니며, 일부 프로세스가 임계 구역에 들어가려는 경우 나머지 프로세스 중 실행 중이지 않은 프로세스만 결정에 참여할 수 있으며 **이 선택은 무한정 연기될 수 없다.**
    - **= deadlock free**
- Bounded Waiting : 프로세스가 자기의 임계구역에 진입하려는 요청을 한 후부터 그 요청이 허용될 때까지 다른 프로세스들이 그들 자신의 임계구역에 진입하도록 허용되는 횟수에 한계가 있어야 한다
    - **= starvation free**

## Peterson algorithm

```c
boolean flag[2];   // 각 프로세스가 임계 구역에 진입하고 싶다는 의사를 표시하는 배열
int turn;          // 현재 임계 구역에 진입할 차례인 프로세스를 나타내는 전역 변수

// P0의 프로세스
do {
    flag[0] = true;
    turn = 1;
    while (flag[1] && turn == 1) {
        // Busy Waiting
    }

    // 임계 구역(Critical Section)

    flag[0] = false;

    // Remainder Section

} while (true);
```

임계 구역이 비어있다면 바로 사용할 수 있고(Progress), 무한정 대기하지 않으므로(Bounded Waiting) 올바른 해결책이다.

### 한계점

1. 피터슨 알고리즘은 Software solution이다. hardware 수준에서는 atomic access를 위해 피터슨 알고리즘이 필요하지 않다.
2. **현대 CPU는 프로그램 실행 속도를 높이기 위해 메모리 접근 순서를 재정렬한다**. 즉, 코드에 작성된 순서와 실제 메모리 접근 순서가 다를 수 있다.
    1. 따라서, 실제 시스템에서는 메모리 접근 순서를 보장하는 메모리 배리어(Memory Barrier)나 원자적 명령어(atomiccaly instruction)을 사용해야한다.
3. 한 프로세스가 임계구역에 진입하면 다른 프로세스는 계속 기다려야한다.(Busy waiting)
4. 두 개의 프로세스에서만 적용이 가능하다
    1. → Bakery algorithm

### reorder memory access

```c
// process p0
int x = 100;
boolean flag = false;

// process p1
while(!flag);  // flag가 false인 동안 대기
print x;
```

**→ x = 100 or 0**

## Synchronization - Hardware

C.S Problem에 대한 해결책으로 하드웨어적 접근이 필요하다!

### atomically instruction

- hardware solution(low-level)
- CPU에서 atomically하게 수행되는 명령어를 이용 → non-interruptible
    - **OS의 도움이 필요 없고, 사용자 모드(User mode)에서 직접 실행될 수 있다(성능 굳)**
- x86 architecture
    - `CMPXCHG` (Compare and Exchange)
    - `XADD` (Exchange and Add)
- 단일 CPU 내에서는 원자적이지만, 멀티코어에서는 LOCK 접두사와 함께 사용된다
    - **이때의 LOCK은 고수준 언어의 LOCK과는 다르다!!**
    - = actomic operation
    - 물론 일반적인 명령어보다는 느리다


📖**Context switches can only happen on interrupts, and interrupts happen before or after an instruction, not in the middle. Any code running on the same CPU will see the `cmpxchg` as either fully executed or not at all**.


### Test and Set(TAS)

```c
boolean TestAndSet(boolean *target) {
    boolean rv = *target;
    *target = true;
    return rv;
}
// hardware-level에서 구현되어 있음!
```

```c
boolean lock = false; // 공유 잠금 변수

// 프로세스 Pi
do {
		while (TestAndSet(&lock)); // 잠금 획득
		
		// 임계 구역(Critical Section) 진입
		
		lock = false; // 작업이 끝나면 잠금 해제
		
		// Remainder section
		
} while(true);
```

- **Bounded waiting을 만족하지 않는다.**
    - 도착한 순서에 관계없이 진입하기 때문!
    - 유한 대기를 보장하기 위해 추가적인 코드를 더하여 해결할 수 있음
    - 복잡하기 때문에 API 형태(Mutex, Semaphore)로 제공된다.
- Spin-Lock을 구현할 수 있다
- *target = true;
    - 메모리에 쓰레기 연산을 반복하여 병목
    - 캐시 일관성 프로토콜을 구현하는 동기화 트래픽 유발
- 낙관적 방법

### Compare and Swap(CAS)

```c
int CompareAndSwap(int* ptr, int expected, int new) {
    int actual = *ptr;
    if (actual == expected)
        *ptr = new;
    return actual;
}
// hardware-level에서 구현되어 있음!

// using
while (CompareAndSwap(&lock->flag, 0, 1) == 1);
```

```nasm
; x86 example
char CompareAndSwap(int* ptr, int old, int new) {
    unsigned char ret;
    __asm__ __volatile__ (
        "lock\n"
        "cmpxchgl %2, %1\n"
        "sete %0\n"
        : "=q" (ret), "=m" (*ptr)
        : "r" (new), "m" (*ptr), "a" (old)
        : "memory"
    );
    return ret;
}
```

- 마찬가지로, atomic operation만으로는 Bounded waiting을 만족하지 않는다.
    - 이를 해결하기 위해 API 형태(Mutex, Semaphore)로 제공된다.
- **Wait-Free Synchronized을 구현할 수 있는 기반 연산**
- Spin-Lock을 구현할 수 있다.
- 필요할 때만 메모리에 write연산을 함
- 낙관적 방법
- **ABA Problem을 해결하고 사용해야한다**
- JAVA에서는  java.util.concurrent.atomic에서 사용가능

### ABA Problem

CAS를 사용할 때 포인터(주소값)이 메모리 관리자에 의해 재사용되면서 생기는 문제

> it is common for the allocated object to be at the same location as the deleted object due to [MRU](https://en.wikipedia.org/wiki/Cache_replacement_policies#Most_recently_used_(MRU))memory allocation

- 해결방법
    - Tagged state reference : 버전 관리
        - Transactional Memory에서도 사용
    - Intermediate nodes : 중간 노드 사용
        - 비용이 커서 비현실적
    - Deferred reclamation
        - GC
        - hazard pointers : 사용 중인 메모리를 삭제하지 않고 가지고 있기
        - RCU(read-copy update)

## Mutex

```c
long long shared_variable = 0;
pthread_mutex_t mutex;

void* run(void* arg) {
    for (int i = 0; i < COUNT; i++) {
        pthread_mutex_lock(&mutex);

        shared_variable++; // 임계 영역 (Critical Section)
        
        pthread_mutex_unlock(&mutex);
    }
    return NULL;
}
```

- 내부 구현
    - Dekker's algorithm
    - Peterson’s Solution
    - Bakery Algorithm(Lamport)
    - Test-And-Set
    - Compare-And-Swap
- 예시
    - C/C++ : POSIX pthread API
    - JAVA - **`synchronized`** 키워드를 제공
    - **x86 어셈블리(assembly)**: 특정 연산에 **`LOCK`** 접두사를 붙여 원자성을 보장

잠금을 획득한(lock) task만이 잠금을 해제(unlock)할 수 있다

Mutex Lock 구현으로는 spin lock을 사용할 수도 있고, 최근에는 sleep lock 방식과 혼용해서 쓰인다. 예를 들어, 락 획득을 위해 먼저 짧은 시간 동안 스핀을 시도하고, 실패하면 슬립 상태로 전환하여 불필요한 CPU 낭비를 줄일 수 있다. Two-Phase Locks이라고도 한다

spin lock은 cpu 자원을 소모하고, sleep lock은 시스템 콜 오버헤드가 존재한다!!

## Semaphore

```c
struct semaphore {
    int value;
    list L; // 프로세스 대기열 (큐)
};

// P 연산 (자원 획득)
function wait(semaphore S):
    S.value--;
    if (S.value < 0):
        add this thread to S.L;
        block();

// V 연산 (자원 해제)
function signal(semaphore S):
    S.value++;
    if (S.value <= 0):
        remove a thread from S.L;
        wakeup(thread);
```

- 소유권 개념이 없음
- 다수의 자원을 관리할 수 있음
- **Starvation을** 피하기 위해 세마포어에는 일반적으로 **FIFO(선입선출)** 방식의 프로세스 큐가 연결
- P와 V의 연산이 분리되어있기 때문에, 잘못 사용할 경우에 대해 예방할 수 없음
    - V를 호출하지 않는 경우 : DeadLock
    - P를 호출하지 않는 경우 : Not Muttal Exclusion

## Mutex vs Semaphores

1. Priority inversion
    1. **Priority inheritance**
    2. …
2. Premature task termination
3. Termination deadlock
4. Recursion deadlock
5. Accidental release

## Monitor

앞서 얘기한 프로그래머의 실수를 예방하기 위한 고급 언어 구조물

뮤텍스와 세마포어가 제공하는 기본 기능을 추상화하여, 개발자가 복잡한 동기화 문제를 더 쉽고 안전하게 다룰 수 있도록 돕는다.

당연히 오버헤드가 존재하며 사용 편의성(+안정성)과 오버헤드 사이의 trade-off이다

- 동작 방식
    - **진입 큐(Entry queue)**: 모니터 내부의 함수를 실행하려는 스레드들은 진입 큐에서 대기하게 됩니다. 모니터는 락(lock) 메커니즘을 내장하고 있어, 큐의 맨 앞에 있는 스레드만이 모니터에 진입할 수 있습니다.
    - **자동 잠금/해제**: 스레드가 모니터 내부 함수에 진입하면 자동으로 잠금이 설정되고, 함수 실행이 끝나면 자동으로 잠금이 해제됩니다. 개발자는 락을 직접 관리할 필요가 없어 실수를 예방할 수 있습니다.
    - **조건 변수**: 모니터 내부에서 특정 조건을 만족하지 못해 대기해야 할 경우, 스레드는 조건 변수를 통해 잠시 모니터를 벗어나 대기 상태에 들어갈 수 있습니다.

In JAVA ⇒ synchronized

## Lock-Free

### **등장 배경(Lock 기반의 단점)**

1. DeadLock & LiveLock을 피하기가 어려움
2. Thread의 Liveness와 관련된 문제들이 발생한다.
    1. Lock Convoy(잠금 호송), Priority Inversion(우선순위 반전), Starvation(고갈), Stampede(쇄도) 등
3. 시스템 콜이 (거의) 필수적이다. 시스템 콜 오버헤드
4. Lock을 사용하는 모듈들 간의 결합이 어렵다
    1. Side Effect를 어떻게 예측하지?
    2. 확장성을 어떻게 설계하지?
    3. Monitor도 결국 오버헤드

### **작동 방식**

- 여러 쓰레드에서 동시에 호출해도, 적어도 한 개의 호출이 완료되는 것이 보장되어야한다
    - 병합 과정에서 적어도 하나의 승자가 존재한다.
    - 전체 시스템 관점에서 진행을 보장한다
- Non-Blocking
    - 다른 스레드의 상태에 상관없이 호출이 완료된다.
- CAS를 활용하여 구현한다!!

### Wait-Free

- 각 스레드 관점에서 진행을 보장한다.

## Transactional Memory(TM)

### **등장 배경**

1. Blocking Algorithm = 앞선 Lock의 문제점 포함
2. Lock-Free(non-blocking algorithm) = 높은 구현 난이도로 인한 생산성 저하
3. CAS는 1Word밖에 지원하지 않음
    1. bus locking 방식으로 동작하기 때문
    2. Multi-Word CAS를 지원하려면 CPU의 지원이 필요하다

사실 멀티스레딩을 위해 개발된 건 아니었다!!

Transmeta 의 Crusoe 및 Efficeon 프로세서에 사용된 게이트 저장 버퍼였다고 한다.

### **작동 방식**

- Software(STM) or hardware(HTM)
    - CPU가 지원하는 HTM은 오버헤드가 없다
    - HTM은 캐시 일관성 프로토콜을 기반으로 구현된다.
        - 트랜잭션 내에서 접근한 메모리는 자기 캐시에만 적용한 뒤, 실제 메모리에 적용하는 시점에 일관성을 검사한다.
        - 이렇게만 해결되는 단순한 문제는 아니고, 다양한 과제들이 존재한다.
- 싱글스레드 프로그램을 그대로 쓰지만 멀티쓰레드에서 Lock-Free하게 실행된다.
    - 다른 스레드와 충돌이 일어날 수 있는 구간을 transaction으로 선언하면 끝!!
    - 데이터베이스의 트랜잭션 개념을 메모리에 적용한 것
    - 스레드에서는 한 번에 하나의 메모리만 접근할 수 있기에 한 스레드당 트랜잭션도 하나임이 보장된다.
- 여러 메모리(word)에 대한 transaction을 허용한다

### **단점**

- 스레드가 많아질 수록 충돌이 많아져 성능이 떨어진다
    - 공유 메모리가 없는 함수형 언어를 사용하면 해결할 수 있음
    - 함수형 언어는 어렵다…

## 변천사 요약

Peterson algorithm 등 소프트웨어적 접근으로 해결하고자 함

→ 구현복잡성, memory reorder 등 문제 발생

→ TAS, CAS 등 하드웨어적 접근(낙관적 방식) & Mutex/Semaphore(비관적 방식) 등을 통해 해결하고자 함

→ Spin Lock 방식 또한 Busy waiting을 해결하지 못함 & 프로그래머의 실수 문제

→ Sleep Lock, two-phase Lock & Monitor

→ Lock 방식의 근본적인 문제가 있다!

→ Lock-Free

→ 구현 난이도가 높음

→ Transactional Memory ! 연구중..

## HISTORY

- 1963년 : Semaphore
- 1965년 : Dekker's algorithm
    - 첫 Mutex 소프트웨어 솔루션
- 1974년 : Monitor
- 1978년 : x86 architecture 등장
    - CMPXCHG 명령어를 통한 첫 하드웨어 솔루션
    - CAS 개념은 더 이전에 등장했을 것으로 추정되지만, 정확한 연도 미상
- 1981년 : Peterson's Algorithm
- 1991년 : CAS for Lock-free
    - 연구를 통해 Lock-free에 적합한 원자적 명령어임이 증명됨
- 2009년 : AMD의 transactional memory 하드웨어 지원 발표

## 사실과 오해

### Mutex의 의미

- 넓은 의미 : 상호 배제(mutual exclusion)의 약자
- 좁은 의미
    1. 한 프로세스의 내부에서 여러 스레드의 임계구역 제어를 위해 사용하는 객체
    2. Locking mechanism (= mutex locks)
- 더 좁은 의미
    - sleep lock만을 사용하는 객체

많은 블로그에서 Mutex에 대한 오해를 낳고 있다.. 대표적으로 아래와 같다.

### Spin Lock vs Mutex ??

간혹 더 좁은 의미에서 Spin Lock과 비교되기도 하지만, 현대의 Mutex는 하이브리드 방식을 사용하기도 하고 Mutex를 Spin Lock 방식으로 구현할 수도 있으므로 적절하지 않은 표현이라고 생각된다.(의미는 전달이 되지만..)

뮤텍스의 핵심은 '오직 하나의 스레드만 임계 구역에 접근하도록 보장하는 것'이며, 이를 어떤 방식으로 구현하느냐는 기술적인 선택의 문제이다. 따라서, 뮤텍스를 스핀 락과 대립되는 개념으로만 정의하는 것은 현대의 기술과는 맞지 않는 오래된 관점이다.

### CAS vs Mutex ??

**비교되는 개념이 아니다**

CAS는 하드웨어 레벨의 동기화 지원 방식으로, **Mutex Locks도 CAS를 사용하여 구현될 수 있다**

CAS가 Lock-Free를 위한 알고리즘이라거나, 소프트웨어 레벨의 알고리즘 혹은 Lock이라는 설명 또한 옳지 않다.

참고로, 과거에는 Peterson’s Solution과 같은 소프트웨어 알고리즘으로 Mutex를 구현했다!

> acquire() 또는 release() 함수 호출은 원자적으로 수행되어야 한다. **따라서 mutex 락은 6.4절에서 설명한 CAS를 사용하여 구현될 수 있다.
…**
>
>
> 6.4절에 요약된 하드웨어 솔루션은 매우 낮은 수준으로 간주하며 일반적으로 mutex 락과 같은 다른 동기화 도구를 구성하기 위한 기초로 사용된다. 그러나 최근 락 오버헤드 없이 경쟁 조건으로부터 보호하는 락 없는(lock-free) 알고리즘을 구현하기 위해 CAS 명령을 사용하는 데 중점을 두고 있다.
> - Operating System(공룡책) p.298, 312
>

## 이제 답변해보자

###  이진 세마포어와 뮤텍스의 차이에 대해 설명해 주세요.

  가장 큰 차이는 소유권입니다. 뮤텍스는 락을 획득한 스레드만이 락을 해제할 수 있습니다. 이러한 차이 때문에, 뮤텍스는 세마포어가 갖는 우선순위 역전, 재귀적 락, Accidental release 등의 문제를 해결할 수 있습니다.

### Lock을 얻기 위해 대기하는 프로세스들은 Spin Lock 기법을 사용할 수 있습니다. 이 방법의 장단점은 무엇인가요? 단점을 해결할 방법은 없을까요?

  Spin Lock은 CPU를 점유하며 무한 루프를 돌기 때문에 Busy waiting이 발생합니다. 또한, 우선순위가 낮은 프로세스가 먼저 스케쥴되는 Priority Inversion이 발생할 수 있습니다.
  먼저, 우선순위 역전 문제를 해결하기 위해 Priority Inheritance Protocol을 적용해 우선순위가 높은 프로세스(P1)가 우선순위가 낮은 프로세스(P3)가 작업 중인 임계 영역에 대기할 때, P3에게 P1의 우선순위를 상속하는 방식 등으로 해결할 수 있습니다. 다만, 이 방식은 소유권 개념이 존재하는 뮤텍스에서만 가능합니다.
  다음으로, Busy waiting을 해결하기 위해 시스템 콜을 활용하는 Sleep Lock을 사용할 수 있습니다. 다만, Context Switching의 오버헤드가 있으므로 이 둘을 결합한 Hybrid Lock을 사용하는 경우가 일반적입니다.

### 뮤텍스와 세마포어 모두 커널이 관리하기 때문에, Lock을 얻고 방출하는 과정에서 시스템 콜을 호출해야 합니다. 이 방법의 장단점이 있을까요? 단점을 해결할 수 있는 방법은 없을까요?

  시스템 콜의 오버헤드를 줄이기 위해 Spin Lock과 결합한 Hybrid Lock을 사용합니다. 또한, Sprin Lock의 구현으로 Atomic Operations를 사용할 수 있습니다. 이는 하드웨어 레벨에서의 동시성 해결 방법으로, CAS를 주로 사용합니다. CAS를 사용하기 위해서는 GC를 사용하는 등 ABA Problem을 반드시 해결해야합니다.


# Deadlock 에 대해 설명해 주세요.

둘 이상의 프로세스가 서로 상대방이 가진 자원을 기다리며 무한정 대기하는 상태

“교착 상태(Deadlock)”란, 두 개 이상의 프로세스(또는 스레드)가 서로가 점유하고 있는 자원을 무한정 기다리면서, 다음 단계로 진행하지 못하고 영구적으로 멈춰있는 상태를 의미한다. 자원을 얻지 못해 작업을 진행할 수 없는데, 그 자원을 가진 다른 프로세스 역시 또 다른 자원을 기다리고 있어 아무도 자원을 해제하지 못하는 악순환이 발생하는 것이다.

비유하자면, 좁은 사거리에서 네 대의 차가 동시에 진입하여 각자 다른 차의 꽁무니를 물고 있는 상황과 같다. 모든 차가 앞으로 가기 위해 다른 차가 비켜주기만을 기다리고 있어, 결국 아무도 움직이지 못하게 된다.

![img_1.png](img_1.png)

다섯 명의 철학자가 하나의 원탁에 앉아 식사를 한다. 각각의 철학자들 사이에는 포크가 하나씩 있고, 앞에는 접시가 있다. 접시 안에 든 요리는 양손에 포크를 하나씩 잡고 사용해 먹어야만 하는 스파게티 이다. 그리고 각각의 철학자는 다른 철학자에게 말을 할 수 없으며, 번갈아가며 각자 식사하거나 생각하는 것만 가능하다. 따라서 식사를 하기 위해서는 왼쪽과 오른쪽의 인접한 철학자가 모두 식사를 하지 않고 생각하는 중이어야 한다. 또한 식사를 마치고 나면, 왼손과 오른손에 든 포크를 다른 철학자가 쓸 수 있도록 내려놓아야 한다. 마지막으로, 각 철학자는 서로가 배가 고픈지 단지 생각하는 중인지 알 수 없다고 가정한다. 이 때, 어떤 철학자도 굶지 않고 식사할 수 있도록 하는 방법은 무엇인가?

식사하는 철학자의 비유

1. 일정 시간 생각을 한다.
2. 왼쪽 포크가 사용 가능해질 때까지 대기한다. 만약 사용 가능하다면 집어든다.
3. 오른쪽 포크가 사용 가능해질 때까지 대기한다. 만약 사용 가능하다면 집어든다.
4. 양쪽의 포크를 잡으면 일정 시간만큼 식사를 한다.
5. 오른쪽 포크를 내려놓는다.
6. 왼쪽 포크를 내려놓는다.
7. 다시 1번으로 돌아간다.

왼쪽 포크와 오른쪽 포크를 한번에(atomic) 가져오는 것이 아니라 순서대로(sequentially) 가져오기 때문에, 모두가 왼쪽 포크부터 집어드는 이런 단순무식한 알고리즘을 가지고 있으면 문제가 생길 수밖에 없다. 5명의 철학자 전부 왼쪽 포크를 들고 있다면 오른쪽 포크를 얻으려고 할 때 오른쪽 포크는 이미 상대방이 가져간 상태이고, 오른쪽 철학자의 오른쪽 역시 가져간 상태이고.. 이렇게 원탁을 한 바퀴 돌아 자기 자신까지 돌아오면 모든 철학자들이 3번 상태에 머무르며 자기 오른쪽 포크가 사용 가능해질 때까지 영원히 기다리고만 있는 DeadLock 상태에 빠지게 된다.

## Deadlock 이 동작하기 위한 4가지 조건에 대해 설명해 주세요.
1. 상호 배제(mutual exclusion)
   매 순간 하나의 자원은 하나의 작업이 점유한다. 즉, 하나의 자원을 얻었다면 독점적으로 사용하며, 다른 작업이 해당 자원을 요청하면 자원이 방출될 때까지 지연(대기)한다.
2. 점유 대기(Hold-and-wait)
   자원을 가진 작업이 하나의 자원을 가진 채 다른 자원을 기다릴 때, 현재 보유한 자원을 놓지 않고 계속 기다린다.
3. 비선점 (no preemption)
   자원을 선점할 수 없다. 즉, 자원이 강제적으로 빼앗기지 않고, 작업이 끝난 이후 자발적으로만 반납(방출)된다.
4. 순환 대기(circular wait)
   자원을 기다리는 작업들 간에 사이클이 형성되어야 한다. (1→2→3→1 )
## 그렇다면 3가지만 충족하면 왜 Deadlock 이 발생하지 않을까요?

예를 들어, 상호 배제, 점유와 대기, 비선점 조건이 모두 만족되더라도, 자원을 요청하는 순서가 원형을 이루지 않는다면(환형 대기 불성립), 언젠가는 자원을 가진 프로세스가 작업을 마치고 자원을 해제하게 됩니다. 그러면 그 자원을 기다리던 다른 프로세스가 작업을 이어갈 수 있어 전체 시스템이 멈추지 않습니다.

## 어떤 방식으로 예방할 수 있을까요?

교착 상태 예방은 4가지 발생 조건 중 하나를 의도적으로 위배하는 방식으로 이루어집니다. 가장 현실적인 방법은 '환형 대기' 조건을 방지하는 것입니다.

- 상호 배제
    - 자원을 작업들이 동시에 사용할 수 있게 하는 방법. 이를 사용하면 동시에 접근이 가능하여 교착상태가 일어나지 않는다.
    - 불가능하다. 어떤 자원은 근본적으로 공유가 불가능(mutex 락, 프린터 등)하고, 동기화에 문제가 생길 수 있다.
- 점유 대기
    - 작업이 자원을 요청할 때마다 다른 자원을 보유하지 않도록 보장. 즉, 하나의 프로토콜이 필요한 모든 자원을 요청하고 할당해야 한다.
    - 자원 이용률이 낮아진다. 요청만 해놓고 안쓰는 자원이 많아진다.
    - 기아상태가 발생할 수 있다. 무한정 대기해야 하는 상황이 발생할 수 있다.
- 비선점
    - 이미 다른 작업이 가지고 있는 자원을 선점한다. 모든 자원을 얻을 수 있을 때, 그 작업이 다시 시작된다.
    - CPU레지스터나 DB트랜젝션처럼, 그 상태가 쉽게 저장되고 복원될 수 있는 자원에 종종 적용된다. 가장 흔하게 교착상태가 발생하는 mutex 락이나 세마포어 같은 자원에는 일반적으로 적용할 수 없다.
- 순환대기
    - 모든 자원 유형에 할당 순서를 정하여 정해진 순서대로만 자원을 할당한다.
      현실적인 접근 방법 (공룡책 의견)

## 왜 현대 OS는 Deadlock을 처리하지 않을까요?

현대의 범용 운영체제가 교착 상태를 적극적으로 처리하지 않는 주된 이유는 성능 저하 문제와 발생 빈도 때문입니다. 즉, 교착 상태를 예방하거나 탐지하는 데 드는 비용이, 그로 인해 얻는 이득보다 크다고 보기 때문입니다.

교착 상태 예방을 위한 규칙을 적용하거나, 주기적으로 교착 상태를 탐지하는 알고리즘을 실행하는 것은 모든 자원 할당 과정에 오버헤드를 추가하여 시스템 전반의 성능을 저하시킵니다.

또한, 범용 OS 환경에서는 교착 상태가 그리 빈번하게 발생하지 않으므로, 이러한 오버헤드를 감수하기보다는 교착 상태 발생 시 시스템을 재시작하는 것이 더 효율적일 수 있습니다. 따라서 이 문제는 보통 데이터베이스 시스템처럼 무결성이 매우 중요하거나, 응용 프로그램 개발자가 직접 처리해야 할 책임으로 남겨두는 경우가 많습니다.

1. 빈번히 발생하는 이벤트가 아니기 때문에 미연에 방지하기 위해 훨씬 더 많은 오버헤드를 들이는것이 비효율적이라고 판단함.
2. 현대 시스템의 복잡성으로 인해 교착 상태를 완전히 방지하는 것은 불가능
3. 만약 시스템에서 deadlock이 발생한 경우 시스템이 비정상적으로 작동한것을 사람이 느낀후 직접 process를 죽이는 방법으로 대처

## Wait Free와 Lock Free를 비교해 주세요.

Lock-Free와 Wait-Free는 모두 락(Lock)을 사용하지 않는 논블로킹 동시성 알고리즘이지만, 보장하는 수준에 차이가 있습니다. 간단히 말해, Lock-Free는 시스템 전체의 진행을 보장하는 것이고, Wait-Free는 모든 개별 스레드의 진행까지 보장하는 더 강력한 조건입니다.

Lock-Free는 여러 스레드가 동시에 접근할 때, 최소 한 개 이상의 스레드는 작업을 성공적으로 마치는 것을 보장합니다. 다른 스레드는 재시도를 할 수 있지만 시스템 전체가 멈추지는 않습니다.

Wait-Free는 여기서 더 나아가, 모든 스레드가 다른 스레드의 작업과 관계없이 유한한 시간 안에 자신의 작업을 반드시 완료하는 것을 보장합니다. 이는 특정 스레드가 무한정 대기하는 '기아 상태(Starvation)'까지 방지하는 가장 강력한 수준의 보장입니다. 따라서 모든 Wait-Free 알고리즘은 Lock-Free이지만, 그 역은 성립하지 않습니다.