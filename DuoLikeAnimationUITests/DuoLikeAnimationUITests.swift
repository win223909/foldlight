import XCTest

final class DuoLikeAnimationUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testDemoAndMainControlsAreAvailableOnLaunch() {
        let app = launchApp()
        XCTAssertTrue(element("demoContent", in: app).waitForExistence(timeout: 10))
        XCTAssertFalse(element("photoContent", in: app).exists)

        for identifier in ["photoPicker", "recalibrate", "settingsButton"] {
            let button = app.buttons[identifier]
            XCTAssertTrue(button.exists, "Missing main control: \(identifier)")
            XCTAssertTrue(button.isHittable, "Main control is obscured: \(identifier)")
        }

        if app.buttons["recalibrate"].isEnabled {
            app.buttons["recalibrate"].tap()
            XCTAssertTrue(element("demoContent", in: app).exists)
        }
        attachScreenshot(of: app, named: "Default demo and controls")
    }

    @MainActor
    func testManualTiltCanBeAdjustedAndSettingsDismissed() {
        let app = launchApp(arguments: ["-tiltDegrees", "0"])
        openSettings(in: app)

        let manualTilt = app.switches["manualTilt"]
        XCTAssertTrue(manualTilt.exists)
        let slider = app.sliders["tiltSlider"]
        if !slider.exists, manualTilt.isEnabled {
            // iOS 27 exposes the whole row as a switch; target its trailing control.
            manualTilt.coordinate(withNormalizedOffset: CGVector(dx: 0.92, dy: 0.5)).tap()
        }
        XCTAssertTrue(slider.waitForExistence(timeout: 5), "Manual mode must reveal the angle slider")

        XCTAssertTrue(slider.exists)
        XCTAssertTrue(slider.isEnabled)
        XCTAssertTrue(slider.isHittable)
        slider.adjust(toNormalizedSliderPosition: 0.2)
        let leftValue = slider.value as? String
        slider.adjust(toNormalizedSliderPosition: 0.8)
        XCTAssertNotEqual(leftValue, slider.value as? String, "The slider must adjust the angle")
        app.buttons["closeSettings"].tap()
        XCTAssertTrue(app.buttons["settingsButton"].waitForExistence(timeout: 5))
        attachScreenshot(of: app, named: "Gaussian glass at intermediate angle")
        openSettings(in: app)
        app.buttons["tiltLeftLimit"].tap()
        XCTAssertEqual(slider.value as? String, "-90 度", "The left endpoint must reach -90 degrees")
        app.buttons["tiltRightLimit"].tap()
        XCTAssertEqual(slider.value as? String, "90 度", "The right endpoint must reach 90 degrees")

        app.buttons["closeSettings"].tap()
        XCTAssertTrue(app.buttons["settingsButton"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["settingsButton"].isHittable)
        XCTAssertFalse(app.buttons["closeSettings"].exists)
        XCTAssertTrue(element("demoContent", in: app).exists)
        attachScreenshot(of: app, named: "Demo at 90 degree endpoint")

        let recalibrate = app.buttons["recalibrate"]
        XCTAssertTrue(recalibrate.isEnabled)
        XCTAssertTrue(recalibrate.isHittable, "Reset must remain accessible at the maximum tilt")
        recalibrate.tap()
        openSettings(in: app)
        XCTAssertEqual(app.sliders["tiltSlider"].value as? String, "0 度", "Recalibration must reset manual tilt")
        app.buttons["closeSettings"].tap()
        XCTAssertTrue(app.buttons["closeSettings"].waitForNonExistence(timeout: 5))
        XCTAssertTrue(element("demoContent", in: app).exists)
        XCTAssertTrue(app.buttons["photoPicker"].isHittable)
        attachScreenshot(of: app, named: "Demo restored after maximum tilt")
    }

    @MainActor
    func testSelectedPhotoCanBeResetToDemo() {
        let app = launchApp(arguments: ["-samplePhoto"])
        XCTAssertTrue(element("photoContent", in: app).waitForExistence(timeout: 10))
        XCTAssertFalse(element("demoContent", in: app).exists)
        attachScreenshot(of: app, named: "Selected photo preview")

        openSettings(in: app)
        let resetPhoto = app.buttons["resetPhoto"]
        XCTAssertTrue(resetPhoto.exists)
        XCTAssertTrue(resetPhoto.isEnabled)
        resetPhoto.tap()
        if app.buttons["closeSettings"].exists {
            app.buttons["closeSettings"].tap()
        }

        XCTAssertTrue(element("demoContent", in: app).waitForExistence(timeout: 5))
        XCTAssertFalse(element("photoContent", in: app).exists)
        XCTAssertTrue(app.buttons["photoPicker"].isHittable)
    }

    @MainActor
    func testSystemPhotoPickerImportsSelectedImage() throws {
        #if !targetEnvironment(simulator)
        throw XCTSkip("系统选图导入测试只在带测试图片的模拟器运行，避免选中使用者的私人照片。")
        #endif
        let app = launchApp()
        app.buttons["photoPicker"].tap()
        let grid = app.scrollViews["photosView_content_scroll_view"]
        let gridAppeared = grid.waitForExistence(timeout: 25)
        print("IMPORT_PICKER_STATE\n" + app.debugDescription)
        XCTAssertTrue(gridAppeared, "System photo grid must finish loading")
        let photo = grid.images.matching(identifier: "PXGGridLayout-Info").firstMatch
        XCTAssertTrue(photo.waitForExistence(timeout: 5), "Seed the dedicated simulator with a test image")
        photo.tap()
        XCTAssertTrue(element("photoContent", in: app).waitForExistence(timeout: 15))
        XCTAssertFalse(app.alerts.firstMatch.exists)
        attachScreenshot(of: app, named: "Imported system-picker photo")
    }

    @MainActor
    func testPhotoPickerOpensAndCanBeCancelled() {
        let app = launchApp()
        XCTAssertTrue(element("demoContent", in: app).waitForExistence(timeout: 10))

        let photoPicker = app.buttons["photoPicker"]
        XCTAssertTrue(photoPicker.isHittable)
        photoPicker.tap()

        let cancel = app.buttons.matching(
            NSPredicate(format: "label == %@ OR label == %@", "Cancel", "取消")
        ).firstMatch
        let pickerOpened = cancel.waitForExistence(timeout: 10)
        let hierarchy = app.debugDescription
        let attachment = XCTAttachment(string: hierarchy)
        attachment.name = "System photo picker accessibility hierarchy"
        attachment.lifetime = .keepAlways
        add(attachment)
        print("System photo picker hierarchy:\n\(hierarchy)")
        attachScreenshot(of: app, named: "System photo picker")

        XCTAssertTrue(pickerOpened, "The system photo picker should expose Cancel or 取消; see hierarchy attachment")
        XCTAssertTrue(cancel.isHittable)
        cancel.tap()

        XCTAssertTrue(cancel.waitForNonExistence(timeout: 5))
        XCTAssertTrue(element("demoContent", in: app).exists)
        XCTAssertFalse(element("photoContent", in: app).exists)
        XCTAssertTrue(photoPicker.isHittable)
        XCTAssertFalse(app.alerts.firstMatch.exists, "Cancelling photo selection should not display an error")
    }

    @MainActor
    func testBottomControlsCanBeHiddenAndRestored() {
        for arguments in [[], ["-samplePhoto"]] as [[String]] {
            let app = launchApp(arguments: arguments)
            let contentID = arguments.isEmpty ? "demoContent" : "photoContent"
            XCTAssertTrue(element(contentID, in: app).waitForExistence(timeout: 10))
            let controlIDs = ["photoPicker", "settingsButton", "recalibrate"]
            for identifier in controlIDs {
                XCTAssertTrue(app.buttons[identifier].isHittable)
            }

            let canvasCenter = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
            canvasCenter.tap()
            for identifier in controlIDs {
                XCTAssertTrue(
                    app.buttons[identifier].waitForNonExistence(timeout: 5),
                    "Tapping \(contentID) should hide \(identifier)"
                )
            }
            XCTAssertTrue(element(contentID, in: app).exists)

            canvasCenter.tap()
            for identifier in controlIDs {
                let button = app.buttons[identifier]
                XCTAssertTrue(button.waitForExistence(timeout: 5))
                XCTAssertTrue(button.isHittable, "Restored control must be tappable: \(identifier)")
            }
            app.terminate()
        }
    }

    @MainActor
    private func launchApp(arguments: [String] = []) -> XCUIApplication {
        XCUIDevice.shared.orientation = .portrait
        let app = XCUIApplication()
        app.launchArguments = ["-uiTesting"] + arguments
        app.launch()
        return app
    }

    @MainActor
    private func element(_ identifier: String, in app: XCUIApplication) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: identifier).firstMatch
    }

    @MainActor
    private func openSettings(in app: XCUIApplication) {
        let settings = app.buttons["settingsButton"]
        XCTAssertTrue(settings.waitForExistence(timeout: 10))
        settings.tap()
        XCTAssertTrue(element("settingsSheet", in: app).waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["closeSettings"].isHittable)
    }

    @MainActor
    private func attachScreenshot(of app: XCUIApplication, named name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
