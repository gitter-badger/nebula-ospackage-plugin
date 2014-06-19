package nebula.plugin.ospackage.daemon

import com.netflix.gradle.plugins.packaging.SystemPackagingPlugin
import nebula.test.IntegrationSpec

class OspackageDaemonPluginLauncherSpec extends IntegrationSpec {

    def 'single daemon'() {
        buildFile << """
            ${applyPlugin(OspackageDaemonPlugin)}
            ${applyPlugin(SystemPackagingPlugin)}
            daemon {
                daemonName = "foobar" // default = packageName
                command = "sleep infinity" // required
            }
            """.stripIndent()

        when:
        runTasksSuccessfully('buildDeb')

        then:
        file("build/distributions/${moduleName}.deb").exists()
    }

    def 'all the bells an whistles'() {
        buildFile << """
            ${applyPlugin(OspackageDaemonPlugin)}
            ${applyPlugin(SystemPackagingPlugin)}
            daemon {
              daemonName = "foobaz" // default = packageName
              command = "sleep infinity" // required
            }
            daemons {
                daemon {
                  // daemonName default = packageName
                  command = 'exit 0'
                }
                daemon {
                  daemonName = "foobar"
                  command = "sleep infinity" // required
                }
                daemon {
                  daemonName = "fooqux" // default = packageName
                  command = "sleep infinity" // required
                  user = "nobody" // default = "root"
                  logCommand = "cronolog /logs/foobar/foobar.log" // default = "multilog t ./main"
                  runLevels = [3,4] // rpm default == [3,4,5], deb default = [2,3,4,5]
                  autoStart = false // default = true
                  startSequence = 99 // default 85
                  stopSequence = 1 // default 15
                }
            }""".stripIndent()

        when:
        runTasksSuccessfully('buildDeb', 'buildRpm')

        then:
        // DEB
        def archivePath = file("build/distributions/${moduleName}_unspecified_all.deb")
        def scan = new com.netflix.gradle.plugins.deb.Scanner(archivePath)

        0555 == scan.getEntry('./service/foobar/run').mode
        0555 == scan.getEntry('./service/foobar/log/run').mode
        0555 == scan.getEntry('./etc/init.d/foobar').mode

        ['/service/foobaz/run', '/service/foobaz/log/run', '/etc/init.d/foobaz'].each {
            scan.getEntry(".${it}").isFile()
        }

        // RPM
        def rpmScan = com.netflix.gradle.plugins.rpm.Scanner.scan(file("build/distributions/${moduleName}-unspecified.noarch.rpm"))
        def files = rpmScan.files.collect { it.name }
        files.any { it == './etc/rc.d/init.d/foobar' }
        files.any { it == './service/foobar/run' }
        files.any { it == './etc/rc.d/init.d/foobaz' }
    }

}
