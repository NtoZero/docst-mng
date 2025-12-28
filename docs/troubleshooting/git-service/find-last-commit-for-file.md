# Git 파일별 마지막 수정 커밋 탐색 문제 해결

## 문제 상황

FULL_SCAN 동기화 시 모든 문서의 `latest_commit_sha`가 최신 커밋으로 동일하게 업데이트되는 문제가 발생했습니다.

### 증상

```
파일: KEY-ROTATION.md
실제 마지막 수정 커밋: ce44393e
DB에 저장된 커밋: d814aef (최신 커밋)
```

모든 파일이 실제로 수정되지 않았음에도 불구하고 스캔 시점의 최신 커밋으로 업데이트되었습니다.

## 원인 분석

### Git 커밋 구조의 이해

Git 커밋은 **전체 프로젝트의 스냅샷**입니다. 변경된 파일만 저장하는 것이 아닙니다.

```
커밋 d814aef (최신):
  ├─ main.java (변경됨) ✏️
  ├─ KEY-ROTATION.md (변경 안됨, 스냅샷에 포함) 📄
  ├─ README.md (변경 안됨, 스냅샷에 포함) 📄
  └─ ... 기타 모든 파일

커밋 ce44393:
  ├─ KEY-ROTATION.md (변경됨) ✏️
  ├─ main.java (변경 안됨, 스냅샷에 포함) 📄
  └─ ...
```

### 잘못된 구현 (PathFilter 사용)

```java
// ❌ 잘못된 코드
public CommitInfo getLastCommitForFile(Git git, String upToCommitSha, String path) {
    try (RevWalk revWalk = new RevWalk(git.getRepository())) {
        ObjectId commitId = git.getRepository().resolve(upToCommitSha);
        RevCommit startCommit = revWalk.parseCommit(commitId);

        revWalk.markStart(startCommit);
        revWalk.setTreeFilter(PathFilter.create(path));  // 문제!

        RevCommit lastCommit = revWalk.next();  // 첫 번째 매칭 커밋 반환
        return toCommitInfo(lastCommit);
    }
}
```

**문제점:**
- `PathFilter`는 **파일이 존재하는지**만 확인
- **파일이 실제로 변경되었는지**는 확인하지 않음
- 최신 커밋의 스냅샷에 파일이 존재하면 무조건 매칭됨

**동작 순서:**
1. 최신 커밋 `d814aef`의 트리를 확인
2. `KEY-ROTATION.md`가 **존재함** (변경되지 않았지만 스냅샷에 포함)
3. `PathFilter` 매치! ✅
4. `d814aef` 반환 ❌ (잘못된 결과)

## 해결 방법

### DiffFormatter를 사용한 올바른 구현

```java
// ✅ 올바른 코드
public CommitInfo getLastCommitForFile(Git git, String upToCommitSha, String path) throws IOException {
    try (RevWalk revWalk = new RevWalk(git.getRepository());
         DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {

        diffFormatter.setRepository(git.getRepository());
        diffFormatter.setDiffComparator(RawTextComparator.DEFAULT);
        diffFormatter.setDetectRenames(true);  // rename 감지

        ObjectId commitId = git.getRepository().resolve(upToCommitSha);
        RevCommit startCommit = revWalk.parseCommit(commitId);
        revWalk.markStart(startCommit);

        // 각 커밋을 순회하면서 파일이 실제로 변경되었는지 확인
        for (RevCommit commit : revWalk) {
            if (commit.getParentCount() == 0) {
                // 최초 커밋 - 파일이 존재하는지 확인
                try (TreeWalk treeWalk = new TreeWalk(git.getRepository())) {
                    treeWalk.addTree(commit.getTree());
                    treeWalk.setRecursive(true);
                    treeWalk.setFilter(PathFilter.create(path));
                    if (treeWalk.next()) {
                        return toCommitInfo(commit);
                    }
                }
            } else {
                // 부모 커밋과 diff 비교
                RevCommit parent = revWalk.parseCommit(commit.getParent(0));
                var diffs = diffFormatter.scan(parent.getTree(), commit.getTree());

                for (DiffEntry diff : diffs) {
                    String diffPath = diff.getNewPath();
                    if (diff.getChangeType() == DiffEntry.ChangeType.DELETE) {
                        diffPath = diff.getOldPath();
                    }

                    if (path.equals(diffPath)) {
                        return toCommitInfo(commit);  // 실제로 변경된 커밋 반환
                    }
                }
            }
        }

        return null;
    }
}
```

**올바른 동작 순서:**
1. `d814aef`와 부모 커밋 비교
2. diff 결과: `main.java`만 변경됨
3. `KEY-ROTATION.md`는 diff에 없음 → 건너뜀 ⏭️
4. 이전 커밋들 계속 검색...
5. `ce44393`와 부모 커밋 비교
6. diff 결과: `KEY-ROTATION.md` 변경됨 ✅
7. `ce44393` 반환 ✅ (정답!)

## 핵심 차이점

| 방법 | 확인 대상 | 결과 |
|------|-----------|------|
| `PathFilter` | 파일이 **존재**하는가? | 최신 커밋 반환 (❌) |
| `DiffFormatter` | 파일이 **변경**되었는가? | 실제 수정 커밋 반환 (✅) |

## 테스트 방법

디버그 로그를 추가하여 확인:

```java
String actualCommitSha = actualCommitInfo.sha();
log.info("Processing file: {} | scan commit: {} | actual last commit: {}",
        path, commitSha.substring(0, 7), actualCommitSha.substring(0, 7));
```

**기대 출력:**
```
Processing file: KEY-ROTATION.md | scan commit: d814aef | actual last commit: ce44393
Processing file: main.java | scan commit: d814aef | actual last commit: d814aef
Processing file: README.md | scan commit: d814aef | actual last commit: a1b2c3d
```

각 파일마다 실제 마지막 수정 커밋이 다르게 표시되어야 합니다.

## 관련 코드

- `GitService.getLastCommitForFile()` - 파일의 마지막 수정 커밋 탐색
- `GitSyncService.processDocument()` - 동기화 시 파일 처리
- `DocumentService.upsertDocument()` - 문서 업데이트

## 참고 자료

- [JGit RevWalk Documentation](https://www.eclipse.org/jgit/documentation/)
- [Git Internals - Git Objects](https://git-scm.com/book/en/v2/Git-Internals-Git-Objects)
