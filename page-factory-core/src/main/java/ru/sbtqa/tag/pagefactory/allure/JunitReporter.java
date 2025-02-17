package ru.sbtqa.tag.pagefactory.allure;

import io.qameta.allure.Allure;
import io.qameta.allure.model.Status;
import io.qameta.allure.model.StatusDetails;
import io.qameta.allure.model.StepResult;
import io.qameta.allure.model.TestResult;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import ru.sbtqa.tag.pagefactory.ApiEndpoint;
import ru.sbtqa.tag.pagefactory.Page;
import ru.sbtqa.tag.pagefactory.annotations.PageEntry;
import ru.sbtqa.tag.pagefactory.annotations.rest.Endpoint;
import ru.sbtqa.tag.pagefactory.properties.Configuration;
import ru.sbtqa.tag.pagefactory.utils.MD5;
import ru.sbtqa.tag.qautils.i18n.I18N;

import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;

public class JunitReporter {

    private static final Configuration PROPERTIES = Configuration.create();

    public static Object handleStep(ProceedingJoinPoint joinPoint) throws Throwable {
        // FIXME: need to get another way to filter junit only steps cuz getStackTrace is very hard
        boolean isFromCucumber = Arrays.stream(
                Thread.currentThread().getStackTrace()
        ).anyMatch(stackTraceElement -> stackTraceElement.getClassName()
                .matches("ru\\.sbtqa\\.tag\\.stepdefs\\.[en|ru]\\..*"));

        if (isFromCucumber) {
            return joinPoint.proceed();
        } else {
            boolean isTestCaseStarted = Allure.getLifecycle().getCurrentTestCase().isPresent();
            if (!isTestCaseStarted) {
                String testCaseId = MD5.hash(joinPoint.getSignature().toShortString());
                String testResultUid = MD5.hash(joinPoint.toLongString());
                Allure.getLifecycle().scheduleTestCase(new TestResult().setTestCaseId(testCaseId).setUuid(testResultUid));
                Allure.getLifecycle().startTestCase(testResultUid);
            }
            Object[] args = normalizeArgs(joinPoint.getArgs());
            String methodName = joinPoint.getSignature().getName();
            // I18n contains template for steps as <methodName><dot><argsCount>. For example: fill.2
            String methodNameWithArgsCount = methodName + "." + args.length;

            String stepUid = createUid(joinPoint);
            String stepNameI18n = getStepNameI18n(joinPoint, methodNameWithArgsCount);
            // if step has i18n template - substitute args to it
            String stepName = String.format((stepNameI18n.equals(methodNameWithArgsCount) ? methodName : stepNameI18n), args);
            Allure.getLifecycle().startStep(stepUid, new StepResult().setName(stepName));
            System.out.println("\t * " + stepName);
            try {
                Object r = joinPoint.proceed();
                Allure.getLifecycle().updateStep(stepUid, stepResult -> stepResult.setStatus(Status.PASSED));
                return r;
            } catch (Throwable t) {
                Allure.getLifecycle().updateStep(stepUid, stepResult ->
                        stepResult.setStatus(Status.FAILED).setStatusDetails(new StatusDetails().setTrace(ExceptionUtils.getStackTrace(t)).setMessage(t.getMessage())));
                throw t;
            } finally {
                attachParameters(methodName, args, createUid(joinPoint), stepName);
                Allure.getLifecycle().stopStep(stepUid);
            }
        }
    }

    private static String getStepNameI18n(ProceedingJoinPoint joinPoint, String method) {
        Locale locale = Locale.forLanguageTag(PROPERTIES.getJunitLang());
        Class clazz = joinPoint.getSignature().getDeclaringType();

        I18N i18n = I18N.getI18n(clazz, locale);

        return i18n.get(method);
    }

    private static String createUid(ProceedingJoinPoint joinPoint) {
        return MD5.hash(joinPoint.getSignature().toLongString()
                + Arrays.toString(normalizeArgs(joinPoint.getArgs()))
                + System.currentTimeMillis());
    }

    private static Object[] normalizeArgs(Object[] args) {
        return Arrays.stream(args).map(arg -> {

            // In case of (action) we have args as Object... and it is array. We need it as String
            if (arg instanceof Object[]) {
                arg = Arrays.toString((Object[]) arg);
            } else if (arg instanceof Class<?>) {
                if (((Class) arg).isAssignableFrom(Page.class)) {
                    arg = ((Class<Page>) arg).getAnnotation(PageEntry.class).title();
                } else if (((Class) arg).isAssignableFrom(ApiEndpoint.class)) {
                    arg = ((Class<ApiEndpoint>) arg).getAnnotation(Endpoint.class).title();
                } else {
                    arg = ((Class) arg).getSimpleName();
                }
            }

            // In case of CONDITION.POSITIVE arg is null and we need at as empty String
            return arg == null ? "" : arg;
        }).toArray();
    }

    private static void attachParameters(String methodName, Object[] args, String uid, String stepName) {
        if (stepName.equals(methodName)) {
            Arrays.stream(args).forEach(arg -> {
                Allure.getLifecycle().startStep(uid + System.currentTimeMillis(),
                        new StepResult().setName(arg.toString()).setStatus(Status.PASSED));
                Allure.getLifecycle().stopStep();
            });
        }
    }
}
