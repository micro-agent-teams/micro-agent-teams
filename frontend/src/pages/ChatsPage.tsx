import { useNavigate } from "react-router";
import { Plus, MessagesSquare, MessageSquarePlus } from "lucide-react";
import * as chat from "@/lib/chat";
import { useAsync } from "@/hooks/useAsync";
import { PageHeader } from "@/components/PageHeader";
import { Avatar } from "@/components/Avatar";
import { Menu, MenuItem } from "@/components/ui/menu";
import { Loading } from "@/components/ui/spinner";
import { Alert, AlertDescription } from "@/components/ui/alert";

export function ChatsPage() {
  const navigate = useNavigate();
  const { data, error, loading } = useAsync(
    () => chat.listThreads({ page_size: 50 }),
    [],
  );

  return (
    <>
      <PageHeader
        title="chats"
        actions={
          <Menu
            trigger={
              <button
                type="button"
                className="text-foreground hover:bg-accent flex size-8 items-center justify-center rounded-md"
                aria-label="new"
              >
                <Plus className="size-5" />
              </button>
            }
          >
            <MenuItem
              icon={<MessageSquarePlus className="size-4" />}
              onSelect={() => navigate("/chats/new")}
            >
              New chat
            </MenuItem>
          </Menu>
        }
      />

      <div className="flex flex-col">
        {loading && <Loading />}
        {error && (
          <div className="p-3">
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          </div>
        )}

        {data && data.threads.length === 0 && (
          <div className="text-muted-foreground flex flex-col items-center gap-2 py-20 text-sm">
            <MessagesSquare className="size-8 opacity-50" />
            no conversations yet
          </div>
        )}

        {data && data.threads.length > 0 && (
          <ul className="flex flex-col">
            {data.threads.map((t) => (
              <li key={t.id}>
                <button
                  type="button"
                  onClick={() => navigate(`/chats/${t.id}`)}
                  className="hover:bg-accent flex w-full items-center gap-3 px-3 py-2.5 text-left"
                >
                  <Avatar
                    seed={t.id}
                    label={t.title || "#"}
                    className="size-12 rounded-lg"
                  />
                  <div className="flex min-w-0 flex-1 flex-col border-b py-1.5">
                    <div className="flex items-baseline justify-between gap-2">
                      <span className="min-w-0 flex-1 truncate font-medium">
                        {t.title || `thread #${t.id}`}
                      </span>
                      <span className="text-muted-foreground shrink-0 text-xs">
                        {fmtListTime(t.updatedAt ?? t.createdAt)}
                      </span>
                    </div>
                    <span className="text-muted-foreground truncate text-sm">
                      tap to open
                    </span>
                  </div>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </>
  );
}

function fmtListTime(iso: string): string {
  const d = new Date(iso);
  const now = new Date();
  const sameDay = d.toDateString() === now.toDateString();
  return sameDay
    ? d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
    : d.toLocaleDateString([], { month: "2-digit", day: "2-digit" });
}
