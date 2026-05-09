# GreenDevOps Build Optimizer — Testing Guide

This guide provides exactly what to change in the codebase to demonstrate the full capabilities of the selective build optimizer. The application has a 4-tier dependency chain:
`core` → `service` → `api` → `app`

By changing files at different levels of this chain, you can force the optimizer to skip different amounts of testing!

---

## Test Scenario 1: The "Leaf Node" Change (Maximum Optimization)
The `app` module is at the very top of the dependency chain. Nothing depends on it. If you change it, the optimizer should skip tests for `core`, `service`, and `api`.

**What to do:**
Open `app/src/main/java/com/greenrelease/app/GreenReleaseDemoApplication.java`
Add a random comment inside the class:
```java
@SpringBootApplication
public class GreenReleaseDemoApplication {
    // TEST SCENARIO 1: Modifying the leaf node
    public static void main(String[] args) {
        SpringApplication.run(GreenReleaseDemoApplication.class, args);
    }
}
```
**Expected Dashboard Result:**
- **Tests Skipped:** 10 (or whatever the total is minus the `app` tests)
- **Modules Built:** `app`
- **Time Saved:** Very High

---

## Test Scenario 2: The "Middle Node" Change (Partial Optimization)
The `service` module sits in the middle. The `api` and `app` modules depend on it, but it depends on `core`. If you change it, the optimizer should test `service`, `api`, and `app`, but safely skip `core`.

**What to do:**
Open `service/src/main/java/com/greenrelease/service/HealthService.java`
Change the version string:
```java
public class HealthService {
    // TEST SCENARIO 2: Middle node change
    private static final String APP_VERSION = "1.0.2"; 
    private static final String HEALTHY_STATUS = "UP";
}
```
**Expected Dashboard Result:**
- **Tests Skipped:** Tests belonging strictly to `core` will be skipped.
- **Modules Built:** `service`, `api`, `app`
- **Time Saved:** Medium

---

## Test Scenario 3: The "Root Node" Change (Zero Optimization)
The `core` module is the foundation. Every other module depends on it. If you break `core`, everything else might break too. The optimizer is smart enough to realize this and will force a full rebuild.

**What to do:**
Open `core/src/main/java/com/greenrelease/core/model/Order.java`
Add a new field to the class:
```java
public class Order {
    private final int id;
    private final int userId;
    // TEST SCENARIO 3: Root node change
    private String status; 
}
```
**Expected Dashboard Result:**
- **Tests Skipped:** 0
- **Modules Built:** `core, service, api, app` (or `all`)
- **Time Saved:** None (Pipeline duration matches the unoptimized pipeline exactly)

---

## Test Scenario 4: The "Documentation" Change (Total Optimization)
The optimizer scans file extensions. It knows that changing a markdown file cannot break Java code.

**What to do:**
Open `README.md` in the root directory.
Add a new line at the bottom:
```markdown
## Testing
Testing the GreenDevOps pipeline for research paper!
```
**Expected Dashboard Result:**
- **Tests Skipped:** 13 (All of them!)
- **Modules Built:** `None`
- **Time Saved:** Extreme (The pipeline skips the build, test, docker, and deploy stages entirely and finishes in ~5 seconds).

---

## Test Scenario 5: The "Global Trigger" Change (Emergency Rebuild)
Certain files govern the entire project (like the root `pom.xml`). If you change a dependency version there, the optimizer cannot guarantee safety, so it forces a total rebuild.

**What to do:**
Open the root `pom.xml`
Change the `<junit.version>` property slightly:
```xml
    <properties>
        <junit.version>4.13.2</junit.version> <!-- Add a comment here to trigger the diff -->
    </properties>
```
**Expected Dashboard Result:**
- **Tests Skipped:** 0
- **Modules Built:** `all`
- **Time Saved:** None
