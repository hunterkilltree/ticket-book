import type { Seat } from '@/types';
import { groupSeats } from '@/utils/seatLayout';

interface Props {
  seats: Seat[];
  selected: Set<string>;
  onToggle: (seatId: string) => void;
}

export function SeatMap({ seats, selected, onToggle }: Props) {
  const grouped = groupSeats(seats);
  return (
    <div className="seatmap">
      {Object.entries(grouped).map(([section, rows]) => (
        <div key={section} className="seatmap-section">
          <h4>Section {section}</h4>
          {Object.entries(rows).map(([row, rowSeats]) => (
            <div key={row} className="seatmap-row">
              <span className="seatmap-rowlabel">{row}</span>
              {rowSeats.map((s) => {
                const isSelected = selected.has(s.id);
                const cls = `seat seat-${s.state.toLowerCase()}${isSelected ? ' seat-selected' : ''}`;
                return (
                  <button
                    key={s.id}
                    type="button"
                    className={cls}
                    disabled={s.state !== 'AVAILABLE'}
                    onClick={() => onToggle(s.id)}
                    title={`${s.section}${s.row}-${s.number} (${s.state})`}
                  >
                    {s.number}
                  </button>
                );
              })}
            </div>
          ))}
        </div>
      ))}
    </div>
  );
}
