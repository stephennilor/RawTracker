import SwiftUI
import WidgetKit
import shared

@main
struct iOSApp: App {
	@Environment(\.scenePhase) private var scenePhase

	var body: some Scene {
		WindowGroup {
			ContentView()
				.onOpenURL { url in
					switch url.host {
					case "capture":
						IosApp.shared.onDeepLinkCapture()
					case "water":
						IosApp.shared.onDeepLinkWater()
					default:
						break
					}
				}
		}
		.onChange(of: scenePhase) { phase in
			if phase != .active {
				WidgetCenter.shared.reloadAllTimelines()
			}
		}
	}
}
