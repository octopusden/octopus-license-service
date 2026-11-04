import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.triggers.finishBuildTrigger

version = "2025.03"

project {
    description = "https://github.com/octopusden/octopus-license-service"

    buildType(CompileAndUt)
    buildType(DeployToOkdQaDev)
    buildType(BuildValidation)
    buildType(Release)
    buildType(ReleasePostProcessing)
    buildType(DeployToOkdQa)
    buildType(DeployToOkdProd)
    buildType(WlValidation)

    params {
        param("OCTOPUS_MODULE_NAME", "octopus-license-service")
        param("COMPONENT_NAME", "license-service")
        param("LAST_RELEASE_VERSION", "%LAST_RELEASE_VERSION_LICENSE_SERVICE%")
        param("OKD_IMAGE_NAME", "license-service")
        param("OCTOPUS_PROVISIONED_BY", "create_repo.py")
    }
}

object CompileAndUt : BuildType({
    templates(AbsoluteId("Octopus_OctopusGradleBuild"))
    id("CompileAndUt")
    name = "[1.0] Compile & UT [AUTO]"

    params {
        // dockerPushImage publishes the image the QA and PROD deploys run.
        param("GRADLE_TASK", "clean build dockerPushImage")
        param("env.JAVA_HOME", "%env.JDK_ZULU_25_x64%")
        param("ARTIFACT_PATH", "")
    }
})

object DeployToOkdQaDev : BuildType({
    templates(AbsoluteId("RnDProcessesAutomation_IdpComponentOkdDeploy"))
    id("DeployToOkdQaDev")
    name = "[2.0] Deploy to OKD QA DEV [AUTO]"

    params {
        text("OKD_SERVER_URL", "%OKD_SERVER_DEV_URL%", allowEmpty = false)
        text("OKD_APPS_DOMAIN", "%OKD_APPS_DOMAIN_DEV%", allowEmpty = false)
        param("BUILD_NUMBER", "${CompileAndUt.depParamRefs.buildNumber}")
    }

    triggers {
        // Default branch only: a pull request build must not replace what QA runs.
        finishBuildTrigger {
            buildType = "${CompileAndUt.id}"
            successfulOnly = true
        }
    }

    dependencies {
        snapshot(CompileAndUt) {
            onDependencyFailure = FailureAction.FAIL_TO_START
            reuseBuilds = ReuseBuilds.SUCCESSFUL
        }
    }

    // The template's VCS labelling is for production deploys only.
    disableSettings("BUILD_EXT_1740")
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
        // The deploy is its own build type, [6.0] and [7.0].
        param("SKIP_DEPLOYMENT_TASK", "true")
        param("TEAMCITY_UPDATE_PROJECT_IDS", "Octopus")
        param("TEAMCITY_UPDATE_PARAMETER_NAME", "LAST_RELEASE_VERSION_LICENSE_SERVICE")
    }
})

object DeployToOkdQa : BuildType({
    templates(AbsoluteId("RnDProcessesAutomation_IdpComponentOkdDeploy"))
    id("DeployToOkdQa")
    name = "[6.0] Deploy to OKD QA [AUTO]"

    params {
        text("OKD_SERVER_URL", "%OKD_SERVER_DEV_URL%", allowEmpty = false)
        text("OKD_APPS_DOMAIN", "%OKD_APPS_DOMAIN_DEV%", allowEmpty = false)
        param("BUILD_NUMBER", "${ReleasePostProcessing.depParamRefs.buildNumber}")
    }

    triggers {
        finishBuildTrigger {
            buildType = "${ReleasePostProcessing.id}"
            successfulOnly = true
        }
    }

    dependencies {
        snapshot(ReleasePostProcessing) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
    }

    // The template's VCS labelling is for production deploys only.
    disableSettings("BUILD_EXT_1740")
})

object DeployToOkdProd : BuildType({
    templates(AbsoluteId("RnDProcessesAutomation_IdpComponentOkdDeploy"))
    id("DeployToOkdProd")
    name = "[7.0] Deploy to OKD PROD [MANUAL]"

    params {
        text("OKD_SERVER_URL", "%OKD_SERVER_PROD_URL%", allowEmpty = false)
        param("DEPLOYMENT_ENVIRONMENT", "production")
        param("HELM_EXTRA_SERVICES_SET", "--set image.name=octopusden/%OKD_IMAGE_NAME%")
        param("BUILD_NUMBER", "${DeployToOkdQa.depParamRefs.buildNumber}")
        text("OKD_SA_TOKEN", "%OKD_SA_PROD_TOKEN%", display = ParameterDisplay.HIDDEN, allowEmpty = true)
    }

    steps {
        // GitHub Packages needs credentials and the upload reads its URL anonymously, so the zip is
        // fetched through the agent's Maven settings first and uploaded from disk, as in
        // octopus-sonar-automation.
        step {
            name = "Download Metarunners"
            id = "Download_Metarunners"
            type = "DownloadArtifact"
            param("ARTIFACT_COORDINATES", "org.octopusden.octopus.license:license-runner:%BUILD_NUMBER%:zip:metarunners")
            param("DOWNLOAD_PATH", "%teamcity.build.checkoutDir%")
            param("UNPACK", "copy")
        }
        step {
            name = "Upload TeamCity Metarunners"
            id = "Upload_TeamCity_Metarunners"
            type = "UploadTeamCityProjectMetarunners"
            param(
                "TEAMCITY_METARUNNERS_ZIP_URL",
                "file://%teamcity.build.checkoutDir%/license-runner-%BUILD_NUMBER%-metarunners.zip",
            )
            param("TEAMCITY_PROJECT_ID", "RDDepartment")
        }
    }

    dependencies {
        snapshot(DeployToOkdQa) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
    }
})

object WlValidation : BuildType({
    templates(AbsoluteId("OctopusWlValidator"))
    id("WlValidation")
    name = "WL Validation"
})
