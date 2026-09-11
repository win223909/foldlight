import SwiftUI

/// Everything is drawn by SwiftUI so Metal can sample the whole surface.
struct DemoContentView: View {
    var body: some View {
        GeometryReader { proxy in
            let compact = proxy.size.height < 760
            VStack(alignment: .leading, spacing: compact ? 18 : 26) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("LIGHT IN MOTION")
                            .font(.system(size: 10, weight: .bold, design: .monospaced))
                            .tracking(3)
                            .foregroundStyle(Color.cyan.opacity(0.85))
                        Text("折光")
                            .font(.system(size: compact ? 34 : 42, weight: .semibold, design: .rounded))
                    }
                    Spacer()
                    Image(systemName: "view.3d")
                        .font(.system(size: 24, weight: .light))
                        .frame(width: 54, height: 54)
                        .background(.white.opacity(0.07), in: .circle)
                }
                ZStack(alignment: .bottomLeading) {
                    RoundedRectangle(cornerRadius: 30)
                        .fill(LinearGradient(colors: [Color(red: 0.2, green: 0.46, blue: 0.96), Color(red: 0.44, green: 0.23, blue: 0.8), Color(red: 0.91, green: 0.43, blue: 0.47)], startPoint: .topLeading, endPoint: .bottomTrailing))
                    GeometryReader { art in
                        Circle()
                            .fill(Color(red: 1, green: 0.79, blue: 0.6))
                            .frame(width: art.size.width * 0.46)
                            .offset(x: art.size.width * 0.48, y: 24)
                        ForEach(0..<6) { index in
                            RoundedRectangle(cornerRadius: 20)
                                .fill(.white.opacity(0.06 + Double(index) * 0.018))
                                .overlay { RoundedRectangle(cornerRadius: 20).stroke(.white.opacity(0.2), lineWidth: 1) }
                                .frame(width: art.size.width * 0.50, height: art.size.height * 0.80)
                                .rotationEffect(.degrees(-26))
                                .offset(x: CGFloat(index) * 31 - 34, y: 12 + CGFloat(index) * 11)
                        }
                    }
                    VStack(alignment: .leading, spacing: 8) {
                        Text("换个角度，\n看见光的形状。")
                            .font(.system(size: compact ? 24 : 29, weight: .semibold))
                            .lineSpacing(4)
                        Text("倾斜 · 透视 · 磨砂")
                            .font(.caption.weight(.medium))
                            .foregroundStyle(.white.opacity(0.75))
                    }
                    .padding(24)
                    .shadow(color: .black.opacity(0.15), radius: 12, y: 3)
                }
                .frame(height: compact ? 220 : min(proxy.size.height * 0.32, 330))
                .clipShape(.rect(cornerRadius: 30))
                HStack(spacing: 14) {
                    tile(symbol: "move.3d", title: "随手而动", detail: "转动手机，感受透视", tint: .cyan)
                    tile(symbol: "photo", title: "你的画面", detail: "用一张照片重新体验", tint: Color(red: 1, green: 0.73, blue: 0.54))
                }
                HStack(spacing: 12) {
                    Image(systemName: "hand.tap").foregroundStyle(.cyan)
                    Text("轻点画面，隐藏按钮，专注于光。")
                        .font(.system(size: 12))
                        .foregroundStyle(.white.opacity(0.48))
                }
                .padding(.horizontal, 4)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 26)
            .padding(.top, max(proxy.safeAreaInsets.top, 60) + 18)
            .padding(.bottom, 150)
            .frame(width: proxy.size.width, height: proxy.size.height, alignment: .top)
            .background(LinearGradient(colors: [Color(red: 0.065, green: 0.105, blue: 0.18), Color(red: 0.035, green: 0.047, blue: 0.08)], startPoint: .topLeading, endPoint: .bottomTrailing))
            .foregroundStyle(.white)
        }
    }

    private func tile(symbol: String, title: String, detail: String, tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Image(systemName: symbol).font(.system(size: 21, weight: .light)).foregroundStyle(tint)
            Text(title).font(.subheadline.weight(.semibold))
            Text(detail).font(.system(size: 10)).foregroundStyle(.white.opacity(0.48))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(18)
        .background(.white.opacity(0.045), in: .rect(cornerRadius: 22))
        .overlay { RoundedRectangle(cornerRadius: 22).strokeBorder(.white.opacity(0.06), lineWidth: 1) }
    }
}
