package com.qnt.safedrive.tests.core.listener;

import com.qnt.safedrive.tests.core.model.constants.Groups;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.SkipException;

/**
 * Gate for {@code master}: once any smoke test did not pass, every regression test is skipped with a
 * clear reason. State is static so it spans the smoke and regression suites of one JVM run.
 */
public class SmokeGateListener implements IInvokedMethodListener {

    private static final AtomicBoolean SMOKE_BROKEN = new AtomicBoolean();
    private static volatile String firstFailure = "";

    @Override
    public void beforeInvocation(IInvokedMethod method, ITestResult result, ITestContext context) {
        if (SMOKE_BROKEN.get() && inGroup(method, Groups.REGRESSION)) {
            throw new SkipException("skipped: smoke failed first (" + firstFailure + ")");
        }
    }

    @Override
    public void afterInvocation(IInvokedMethod method, ITestResult result, ITestContext context) {
        if (method.isTestMethod() && inGroup(method, Groups.SMOKE) && result.getStatus() != ITestResult.SUCCESS) {
            if (SMOKE_BROKEN.compareAndSet(false, true)) {
                firstFailure = result.getMethod().getQualifiedName();
            }
        }
    }

    private static boolean inGroup(IInvokedMethod method, String group) {
        return method.isTestMethod() && Arrays.asList(method.getTestMethod().getGroups()).contains(group);
    }
}
