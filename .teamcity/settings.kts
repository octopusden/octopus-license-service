import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.triggers.finishBuildTrigger

version = "2025.03"

project {
    description = "https://github.com/octopusden/octopus-license-service"

    buildType(CompileAndUt)
    buildType(BuildValidation)
    buildType(Release)
    buildType(ReleasePostProcessing)
    buildType(WlValidation)

    params {
        param("OCTOPUS_MODULE_NAME", "octopus-license-service")
        param("LAST_RELEASE_VERSION", "%LAST_RELEASE_VERSION_LICENSE_SERVICE%")
        param("OKD_IMAGE_NAME", "")
        param("OCTOPUS_PROVISIONED_BY", "create_repo.py")
    }
}

object CompileAndUt : BuildType({
    templates(AbsoluteId("Octopus_OctopusGradleBuild"))
    id("CompileAndUt")
    name = "[1.0] Compile & UT [AUTO]"

    params {
        param("GRADLE_TASK", "clean build")
        param("env.JAVA_HOME", "%env.JDK_21_0_x64%")
        param("ARTIFACT_PATH", "")
    }
})

object BuildValidation : BuildType({
    templates(AbsoluteId("RDDepartment_PostGithubStatus"))
    id("BuildValidation")
    name = "[3.0] Build Validation [AUTO]"

    triggers {
        finishBuildTrigger {
            buildType = "${CompileAndUt.id}"
            successfulOnly = false
            branchFilter = "+:*"
        }
    }

    dependencies {
        snapshot(CompileAndUt) {
            onDependencyFailure = FailureAction.ADD_PROBLEM
            reuseBuilds = ReuseBuilds.SUCCESSFUL
        }
    }
})

object Release : BuildType({
    templates(AbsoluteId("Octopus_OctopusComponents_OctopusRelease"))
    id("Release")
    name = "[4.0] Release [MANUAL]"

    params {
        param("BUILD_NUMBER", "${CompileAndUt.depParamRefs.buildNumber}")
        param("CURRENT_COMMIT", "${CompileAndUt.depParamRefs["CURRENT_COMMIT"]}")
        param("PROJECT_VERSION", "${CompileAndUt.depParamRefs["PROJECT_VERSION"]}")
    }

    dependencies {
        snapshot(CompileAndUt) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
        snapshot(BuildValidation) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
    }
})

object ReleasePostProcessing : BuildType({
    templates(AbsoluteId("Octopus_OctopusComponents_HOctopusTest_OctopusReleasePostProcessing"))
    id("ReleasePostProcessing")
    name = "[5.0] Release Post Processing [AUTO]"

    params {
        param("SKIP_DEPLOYMENT_TASK", "true")
        param("TEAMCITY_UPDATE_PROJECT_IDS", "Octopus")
        param("TEAMCITY_UPDATE_PARAMETER_NAME", "LAST_RELEASE_VERSION_LICENSE_SERVICE")
    }
})

object WlValidation : BuildType({
    templates(AbsoluteId("OctopusWlValidator"))
    id("WlValidation")
    name = "WL Validation"
})
