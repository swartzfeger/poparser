PO Parser is a Kotlin Multiplatform project targeting Desktop (JVM).
The application source is in [jvmMain](./composeApp/src/jvmMain/kotlin).

### Build and Run Desktop (JVM) Application

To build and run the development version of the desktop app, use the run configuration from the run widget
in your IDE’s toolbar or run it directly from the terminal:

- on macOS/Linux
  ```shell
  ./gradlew :composeApp:run
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:run
  ```

---

### Build Windows installers from GitHub

Go to **Actions → Build Windows Installers → Run workflow** after the workflow
is on the default branch. It uses the existing Gradle packaging tasks on
Windows x64 with Java 21 and WiX. No macOS installer is built.

- Leave the draft option unchecked to build only. Download the EXE, MSI, and
  `release-notes.md` from the run's **Artifacts** section within seven days.
- Check the draft option to create a GitHub Release draft with both installers.
  Review and publish it under **Releases**. The `POParserVX.Y.Z` tag targets the
  built commit and is created when the draft is published. The workflow will
  not replace an existing tag or release.

The installer and About box versions must agree. Notes include commit subjects
since the previous reachable `POParserV` tag. The Supported Customers date
defaults to the built commit's date; enter the push date as `YYYY-MM-DD` if
different. Update the [supported layout roster](tools/supported-customers.txt)
when adding layouts. Release notes list roster entries active in the bundled
customer master.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…
