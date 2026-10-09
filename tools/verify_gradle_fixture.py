"""Developer-only integration check with real offline Gradle in an owned fixture."""
import json
import pathlib
import sys
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parent.parent/'runner'))
import test_runner as fixture

home=pathlib.Path.home()
source=pathlib.Path(__file__).resolve().parent.parent
junit=list((home/'.gradle/caches/modules-2/files-2.1/junit/junit/4.13.2').glob('**/junit-4.13.2.jar'))
hamcrest=list((home/'.gradle/caches/modules-2/files-2.1/org.hamcrest/hamcrest-core/1.3').glob('**/hamcrest-core-1.3.jar'))
assert junit and hamcrest,'Existing offline JUnit cache is required; no downloads.'
f=fixture.Fixture();f.setUp()
try:
    app=f.repo/'app';tests=app/'src/test/java';tests.mkdir(parents=True)
    (f.repo/'settings.gradle').write_text("rootProject.name='AnimaDevIsolatedFixture'\ninclude ':app'\n")
    (f.repo/'gradle.properties').write_text('org.gradle.workers.max=2\norg.gradle.jvmargs=-Xmx512m\n')
    (app/'build.gradle').write_text("plugins { id 'java' }\ndependencies { testImplementation files("+repr(str(junit[0]))+","+repr(str(hamcrest[0]))+") }\ntasks.register('testDebugUnitTest', Test) { dependsOn tasks.named('testClasses'); testClassesDirs=sourceSets.test.output.classesDirs; classpath=sourceSets.test.runtimeClasspath }\n")
    (tests/'RunnerFixtureTest.java').write_text('import org.junit.Test; import org.junit.Ignore; import static org.junit.Assert.*; public class RunnerFixtureTest { @Test public void pass(){assertTrue(true);} @Test public void fail(){assertTrue(false);} @Ignore @Test public void skip(){assertTrue(true);} }')
    (f.repo/'gradlew').write_text('#!/bin/bash\nexec bash "'+str(source/'gradlew')+'" "$@"\n')
    fixture.git(f.repo,'add','.');fixture.git(f.repo,'commit','-m','isolated offline Gradle fixture')
    before=fixture.git(f.repo,'rev-parse','HEAD')
    job=f.run_action('TEST')
    print(json.dumps({'state':job['state'],'exit_code':job['exit_code'],'error_code':job['error_code'],'tests':job.get('tests'),'stdout':job['stdout'],'stderr':job['stderr']},ensure_ascii=False))
    assert job['state']=='FAILED' and job['exit_code']==1 and job['error_code']=='TESTS_FAILED'
    assert job['tests']['total']==3 and job['tests']['passed']==1 and job['tests']['failed']==1 and job['tests']['skipped']==1
    assert fixture.git(f.repo,'rev-parse','HEAD')==before
    assert fixture.git(f.repo,'diff','--name-only')==''
    print('REAL_OFFLINE_GRADLE_FIXTURE_OK: counts and exit match real JUnit, tracked sources unchanged.')
finally:f.tearDown()
