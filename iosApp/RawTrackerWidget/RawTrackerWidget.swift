import WidgetKit
import SwiftUI

private let appGroup = "group.com.rawtracker.app"
private let canvas = Color(red: 1.0, green: 0.914, blue: 0.808)   // 0xFFFFE9CE
private let ink = Color(red: 0.541, green: 0.325, blue: 1.0)      // 0xFF8A53FF

struct MacroEntry: TimelineEntry {
    let date: Date
    let cal: Int
    let protein: Int
    let carbs: Int
    let fat: Int
    let goalCal: Int
}

struct Provider: TimelineProvider {
    func placeholder(in context: Context) -> MacroEntry {
        MacroEntry(date: Date(), cal: 0, protein: 0, carbs: 0, fat: 0, goalCal: 2500)
    }

    func getSnapshot(in context: Context, completion: @escaping (MacroEntry) -> Void) {
        completion(loadEntry())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<MacroEntry>) -> Void) {
        let next = Calendar.current.date(byAdding: .minute, value: 15, to: Date()) ?? Date()
        completion(Timeline(entries: [loadEntry()], policy: .after(next)))
    }

    private func loadEntry() -> MacroEntry {
        let d = UserDefaults(suiteName: appGroup)
        let goal = d?.integer(forKey: "goalCal") ?? 0
        return MacroEntry(
            date: Date(),
            cal: d?.integer(forKey: "cal") ?? 0,
            protein: d?.integer(forKey: "protein") ?? 0,
            carbs: d?.integer(forKey: "carbs") ?? 0,
            fat: d?.integer(forKey: "fat") ?? 0,
            goalCal: goal == 0 ? 2500 : goal
        )
    }
}

struct ProgressWidgetView: View {
    var entry: MacroEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text("TODAY")
                .font(.system(size: 12, weight: .bold, design: .monospaced)).foregroundColor(ink)
            Text("\(entry.cal)")
                .font(.system(size: 30, weight: .heavy, design: .monospaced)).foregroundColor(ink)
            Text("/ \(entry.goalCal) kcal")
                .font(.system(size: 11, design: .monospaced)).foregroundColor(ink.opacity(0.6))
            HStack(spacing: 10) {
                Text("P\(entry.protein)").macro()
                Text("C\(entry.carbs)").macro()
                Text("F\(entry.fat)").macro()
            }
            Spacer(minLength: 4)
            HStack(spacing: 8) {
                Link(destination: URL(string: "rawtracker://capture")!) {
                    Text("+ FOOD")
                        .font(.system(size: 12, weight: .bold, design: .monospaced))
                        .foregroundColor(canvas)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(ink)
                        .cornerRadius(6)
                }
                Link(destination: URL(string: "rawtracker://water")!) {
                    Text("+ H\u{2082}O")  // subscript two, matching the Android widget
                        .font(.system(size: 12, weight: .bold, design: .monospaced))
                        .foregroundColor(ink)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .overlay(RoundedRectangle(cornerRadius: 6).stroke(ink, lineWidth: 2))
                }
            }
        }
    }
}

private extension Text {
    func macro() -> some View {
        self.font(.system(size: 14, weight: .bold, design: .monospaced)).foregroundColor(ink)
    }
}

struct CameraWidgetView: View {
    var body: some View {
        Link(destination: URL(string: "rawtracker://capture")!) {
            ZStack {
                ink
                Text("\u{1F4F7}").font(.system(size: 30))
            }
        }
    }
}

@available(iOS 17.0, *)
private func widgetBackground<V: View>(_ view: V) -> some View {
    view.containerBackground(canvas, for: .widget)
}

struct ProgressWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "ProgressWidget", provider: Provider()) { entry in
            if #available(iOS 17.0, *) {
                ProgressWidgetView(entry: entry).padding(14).containerBackground(canvas, for: .widget)
            } else {
                ProgressWidgetView(entry: entry).padding(14).background(canvas)
            }
        }
        .configurationDisplayName("Today's Macros")
        .description("Calories, protein, carbs, fat + quick log.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

struct CameraWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "CameraWidget", provider: Provider()) { _ in
            if #available(iOS 17.0, *) {
                CameraWidgetView().containerBackground(ink, for: .widget)
            } else {
                CameraWidgetView()
            }
        }
        .configurationDisplayName("Quick Capture")
        .description("Tap to photograph a meal.")
        .supportedFamilies([.systemSmall])
    }
}

@main
struct RawTrackerWidgetBundle: WidgetBundle {
    var body: some Widget {
        ProgressWidget()
        CameraWidget()
    }
}
